package ch.lostkeyfinder.operations

import com.mongodb.ConnectionString
import com.mongodb.client.MongoClients
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64

/** Operator-only tooling. No database contents, email addresses or key values are printed. */
class SchemaMigration(
    private val template: MongoTemplate,
) {
    fun inspect(): Map<String, Long> =
        listOf("tagCreationLimits", "publicRequestLimits", "rateLimitKeys", "rateLimits")
            .associateWith { template.getCollection(it).countDocuments() }

    fun importCounters() {
        val day = LocalDate.now(ZoneOffset.UTC).toString()
        for (source in template.getCollection("tagCreationLimits").find()) {
            val id = source.getString("_id")
            val parts = id.split(':')
            if (parts.size != 2 || parts[1] != day) continue
            val count = (source["count"] as Number).toLong()
            require(count >= 0)
            val userId = parts[0]
            template.updateFirst(
                query(
                    where("_id").`is`(userId).orOperator(
                        where("tagCreationLimit.day").exists(false),
                        where("tagCreationLimit.day").lt(day),
                    ),
                ),
                Update.update("tagCreationLimit", mapOf("day" to day, "count" to 0)),
                "users",
            )
            // CAS against the previously imported value lets a repeated pass import only late deltas.
            while (true) {
                val user = template.findById(userId, Document::class.java, "users") ?: break
                val window = user.get("tagCreationLimit", Document::class.java) ?: break
                if (window.getString("day") != day) break
                val previous = (window["legacyImportedCount"] as? Number)?.toLong() ?: 0
                if (count <= previous) break
                val expected =
                    if (previous == 0L) {
                        where("tagCreationLimit.legacyImportedCount").`in`(null, 0)
                    } else {
                        where("tagCreationLimit.legacyImportedCount").`is`(previous)
                    }
                val changed =
                    template.updateFirst(
                        query(
                            where("_id")
                                .`is`(userId)
                                .and("tagCreationLimit.day")
                                .`is`(day)
                                .andOperator(expected),
                        ),
                        Update().inc("tagCreationLimit.count", count - previous).set("tagCreationLimit.legacyImportedCount", count),
                        "users",
                    )
                if (changed.modifiedCount == 1L) break
            }
        }
        for (source in template.getCollection("publicRequestLimits").find()) {
            if (source.getDate("expiresAt")?.toInstant()?.isAfter(Instant.now()) != true) continue
            val id = source.getString("_id")
            val count = (source["count"] as Number).toLong()
            require(count >= 0)
            val selection = query(where("_id").`is`(id))
            template.upsert(selection, Update().setOnInsert("count", 0).setOnInsert("expiresAt", source["expiresAt"]), "rateLimits")
            while (true) {
                val target = requireNotNull(template.findById(id, Document::class.java, "rateLimits"))
                val previous = (target["legacyImportedCount"] as? Number)?.toLong() ?: 0
                if (count <= previous) break
                val expected =
                    if (previous ==
                        0L
                    ) {
                        where("legacyImportedCount").`in`(null, 0)
                    } else {
                        where("legacyImportedCount").`is`(previous)
                    }
                val changed =
                    template.updateFirst(
                        query(where("_id").`is`(id).andOperator(expected)),
                        Update().inc("count", count - previous).set("legacyImportedCount", count),
                        "rateLimits",
                    )
                if (changed.modifiedCount == 1L) break
            }
        }
    }

    fun legacyKey(): String? = template.findById("public-v1", Document::class.java, "rateLimitKeys")?.getString("key")

    fun cleanup(
        configuredKey: String,
        legacyWritersStopped: Boolean,
    ) {
        require(legacyWritersStopped) { "Stop legacy writers, including local preview backends, before cleanup" }
        require(Base64.getDecoder().decode(configuredKey).size >= 32)
        require(legacyKey()?.let { it == configuredKey } != false) { "Configured key differs; do not reset active limits" }
        importCounters()
        for (name in listOf("tagCreationLimits", "publicRequestLimits", "rateLimitKeys")) {
            if (template.collectionExists(name)) template.dropCollection(name)
        }
        template.updateMulti(
            query(where("tagCreationLimit.legacyImportedCount").exists(true)),
            Update().unset("tagCreationLimit.legacyImportedCount"),
            "users",
        )
        template.updateMulti(query(where("legacyImportedCount").exists(true)), Update().unset("legacyImportedCount"), "rateLimits")
    }
}

fun main(args: Array<String>) {
    val uri = requireNotNull(System.getenv("FIRESTORE_MONGODB_URI"))
    MongoClients.create(uri).use { client ->
        val migration = SchemaMigration(MongoTemplate(client, requireNotNull(ConnectionString(uri).database)))
        when (args.firstOrNull() ?: "inspect") {
            "inspect" -> {
                println(migration.inspect())
            }

            "prepare-secret" -> {
                require(System.getenv("MIGRATION_PROJECT") == "lost-key-finder-dev") { "This helper is restricted to the Dev project" }
                val key = requireNotNull(migration.legacyKey()) { "No legacy key; provision a new random key separately" }
                require(Base64.getDecoder().decode(key).size >= 32)
                val project = "--project=lost-key-finder-dev"
                val name = "public-rate-limit-hmac"

                fun run(vararg command: String): Pair<Int, ByteArray> {
                    val process = ProcessBuilder(*command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
                    val output = process.inputStream.readBytes()
                    return process.waitFor() to output
                }
                val existing = run("gcloud", "secrets", "versions", "access", "1", "--secret=$name", project)
                if (existing.first == 0) {
                    require(
                        existing.second.toString(Charsets.UTF_8).trim() == key,
                    ) { "Existing secret differs; manual investigation required" }
                } else {
                    val describe = run("gcloud", "secrets", "describe", name, project)
                    if (describe.first !=
                        0
                    ) {
                        require(run("gcloud", "secrets", "create", name, project, "--replication-policy=automatic").first == 0)
                    } else {
                        val versions = run("gcloud", "secrets", "versions", "list", name, project, "--format=value(name)")
                        require(
                            versions.first == 0 && versions.second.isEmpty(),
                        ) { "Existing version cannot be verified; no new version created" }
                    }
                    val add =
                        ProcessBuilder("gcloud", "secrets", "versions", "add", name, project, "--data-file=-")
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start()
                    add.outputStream.use { it.write(key.toByteArray()) }
                    require(add.waitFor() == 0) { "Secret creation failed" }
                    require(
                        run(
                            "gcloud",
                            "secrets",
                            "versions",
                            "access",
                            "1",
                            "--secret=$name",
                            project,
                        ).second.toString(Charsets.UTF_8).trim() ==
                            key,
                    )
                }
                println("Existing HMAC key copied and verified in Secret Manager; no database data removed.")
            }

            "import" -> {
                require("--apply" in args)
                migration.importCounters()
                println("Counters imported; legacy data retained.")
            }

            "cleanup" -> {
                require("--apply" in args)
                migration.cleanup(requireNotNull(System.getenv("PUBLIC_RATE_LIMIT_HMAC_KEY")), "--legacy-writers-stopped" in args)
                println("Legacy technical collections removed after final import.")
            }

            else -> {
                error("Use inspect, prepare-secret, import --apply or cleanup --apply --legacy-writers-stopped")
            }
        }
    }
}
