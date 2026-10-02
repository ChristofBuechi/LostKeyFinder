package ch.lostkeyfinder

import com.mongodb.client.MongoClients
import org.bson.Document
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import java.util.UUID
import kotlin.test.assertEquals

class FirestoreIntegrationTest {
    @Test fun `real Firestore supports Spring Data session transaction and readback`() {
        val uri =
            requireNotNull(System.getenv("FIRESTORE_MONGODB_URI")) {
                "FIRESTORE_MONGODB_URI is required for explicit provider verification"
            }
        val database = requireNotNull(com.mongodb.ConnectionString(uri).database)
        MongoClients.create(uri).use { client ->
            val template = MongoTemplate(client, database)
            val id = UUID.randomUUID().toString()
            client.startSession().use { session ->
                try {
                    template.executeCommand(Document("ping", 1))
                    session.withTransaction<Boolean> {
                        template.withSession { session }.execute<Boolean> { operations ->
                            operations.insert(Document("_id", id).append("value", 1), "integrationProbes")
                            operations.updateFirst(
                                org.springframework.data.mongodb.core.query.Query.query(
                                    org.springframework.data.mongodb.core.query.Criteria
                                        .where("_id")
                                        .`is`(id),
                                ),
                                org.springframework.data.mongodb.core.query.Update
                                    .update("value", 2),
                                "integrationProbes",
                            )
                            true
                        } ?: false
                    }
                    assertEquals(2, template.findById(id, Document::class.java, "integrationProbes")?.getInteger("value"))
                } finally {
                    template.remove(
                        org.springframework.data.mongodb.core.query.Query.query(
                            org.springframework.data.mongodb.core.query.Criteria
                                .where("_id")
                                .`is`(id),
                        ),
                        "integrationProbes",
                    )
                }
            }
        }
    }
}
