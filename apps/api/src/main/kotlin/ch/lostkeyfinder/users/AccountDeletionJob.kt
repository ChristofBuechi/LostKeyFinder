package ch.lostkeyfinder.users

import ch.lostkeyfinder.jobs.GoogleJobAuthentication
import io.swagger.v3.oas.annotations.Operation
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@ConfigurationProperties("account-deletion-job")
data class AccountDeletionJobProperties(
    val audience: String = "",
    val serviceAccount: String = "",
)

@Configuration
@EnableConfigurationProperties(AccountDeletionJobProperties::class)
class AccountDeletionJobConfiguration

@Service
class AccountDeletionWorker(
    private val template: MongoTemplate,
    private val processor: AccountDeletionProcessor,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun dispatch(): Int = dueJobs().count { processDueJob(it.id) }

    internal fun dueJobs(): List<AccountDeletion> =
        template.find(query(due(Instant.now())).with(Sort.by("createdAt")).limit(10), AccountDeletion::class.java)

    internal fun processDueJob(id: String): Boolean {
        val now = Instant.now()
        val token = UUID.randomUUID().toString()
        val job =
            template.findAndModify(
                query(Criteria().andOperator(due(now), where("_id").`is`(id))),
                Update.update("leaseToken", token).set("leaseUntil", now.plusSeconds(300)),
                FindAndModifyOptions.options().returnNew(true),
                AccountDeletion::class.java,
            ) ?: return false
        val owned =
            query(
                where("_id")
                    .`is`(job.id)
                    .and("leaseToken")
                    .`is`(token)
                    .and("completedAt")
                    .`is`(null),
            )
        try {
            if (!processor.process(job, token)) {
                template.updateFirst(
                    owned,
                    Update.update("nextAttemptAt", Instant.now().plusSeconds(60)).set("leaseUntil", Instant.EPOCH).unset("leaseToken"),
                    AccountDeletion::class.java,
                )
            }
        } catch (ex: Exception) {
            logger.warn("Account deletion processing failed; retry scheduled")
            // Persist only fixed metadata; provider errors can contain identifiers or credentials.
            val delay = minOf(3600L, 60L * (1L shl minOf(job.failures, 6)))
            template.updateFirst(
                owned,
                Update
                    .update("nextAttemptAt", Instant.now().plusSeconds(delay))
                    .set("lastFailureAt", Instant.now())
                    .inc("failures", 1)
                    .set("leaseUntil", Instant.EPOCH)
                    .unset("leaseToken"),
                AccountDeletion::class.java,
            )
        }
        return true
    }

    private fun due(now: Instant) =
        Criteria().andOperator(
            where("completedAt").`is`(null),
            Criteria().orOperator(where("nextAttemptAt").lte(now), where("nextAttemptAt").exists(false)),
            Criteria().orOperator(where("leaseUntil").lte(now), where("leaseUntil").exists(false)),
        )
}

@RestController
class AccountDeletionJob(
    private val worker: AccountDeletionWorker,
    private val properties: AccountDeletionJobProperties,
    private val authentication: GoogleJobAuthentication,
) {
    @PostMapping("/api/v1/internal/account-deletions/dispatch")
    @Operation(hidden = true)
    fun dispatch(
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): Map<String, Int> {
        authentication.requireServiceAccount(authorization, properties.audience, properties.serviceAccount)
        return mapOf("processed" to worker.dispatch())
    }
}
