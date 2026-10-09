package ch.lostkeyfinder

import ch.lostkeyfinder.identity.IdentityProperties
import ch.lostkeyfinder.identity.SecurityConfiguration
import ch.lostkeyfinder.identity.SupabaseIdentity
import ch.lostkeyfinder.users.User
import ch.lostkeyfinder.users.UserService
import ch.lostkeyfinder.users.UserStatus
import com.mongodb.ConnectionString
import com.mongodb.client.MongoClients
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.mock.env.MockEnvironment
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class FirestoreUserIntegrationTest {
    @Test fun `real Firestore enforces unique provisioning and local user status`() {
        val uri = requireNotNull(System.getenv("FIRESTORE_MONGODB_URI"))
        val subjects = listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        MongoClients.create(uri).use { client ->
            val template = MongoTemplate(client, requireNotNull(ConnectionString(uri).database))
            TestIdentityProvider().use { identity ->
                val properties = IdentityProperties(URI(identity.origin), "test-publishable-key")
                val decoder = SecurityConfiguration().jwtDecoder(properties, MockEnvironment())
                val users = UserService(template, SupabaseIdentity(properties, RestClient.builder()))
                val jwt = decoder.decode(identity.token(subject = subjects[0]))
                val selection = query(where("supabaseUserId").`in`(subjects))
                try {
                    val ids =
                        Executors.newFixedThreadPool(4).use { executor ->
                            (1..8)
                                .map { executor.submit<String> { users.currentUser(jwt).id } }
                                .map { it.get(30, TimeUnit.SECONDS) }
                        }
                    assertEquals(1, ids.toSet().size)
                    val first = users.currentUser(jwt)
                    assertFailsWith<DuplicateKeyException> { template.insert(first.copy(id = UUID.randomUUID().toString())) }
                    val other = users.currentUser(decoder.decode(identity.token(subject = subjects[1])))
                    assertNotEquals(ids.first(), other.id)
                    assertEquals("owner@example.com", other.email)
                    assertEquals(2L, template.count(selection, User::class.java))
                    template.updateFirst(
                        query(where("supabaseUserId").`is`(subjects[0])),
                        Update.update("status", UserStatus.DISABLED),
                        User::class.java,
                    )
                    assertEquals(403, assertFailsWith<ResponseStatusException> { users.currentUser(jwt) }.statusCode.value())
                } finally {
                    template.remove(selection, User::class.java)
                }
            }
        }
    }
}
