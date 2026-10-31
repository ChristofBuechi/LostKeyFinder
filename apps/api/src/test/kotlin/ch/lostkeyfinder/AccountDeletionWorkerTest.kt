package ch.lostkeyfinder

import ch.lostkeyfinder.identity.FirebaseAdmin
import ch.lostkeyfinder.identity.FirebaseIdentity
import ch.lostkeyfinder.jobs.GoogleJobAuthentication
import ch.lostkeyfinder.tags.Tag
import ch.lostkeyfinder.tags.TagOrigin
import ch.lostkeyfinder.tags.TagStatus
import ch.lostkeyfinder.tags.TagTransactions
import ch.lostkeyfinder.users.AccountDeletion
import ch.lostkeyfinder.users.AccountDeletionJob
import ch.lostkeyfinder.users.AccountDeletionJobProperties
import ch.lostkeyfinder.users.AccountDeletionProcessor
import ch.lostkeyfinder.users.AccountDeletionService
import ch.lostkeyfinder.users.AccountDeletionWorker
import ch.lostkeyfinder.users.User
import ch.lostkeyfinder.users.UserService
import ch.lostkeyfinder.users.UserStatus
import ch.lostkeyfinder.users.deletionKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("ci")
@Import(InMemoryMongoConfiguration::class, TestIdentityConfiguration::class)
class AccountDeletionWorkerTest {
    @Autowired lateinit var template: MongoTemplate

    @Autowired lateinit var worker: AccountDeletionWorker

    @Autowired lateinit var processor: AccountDeletionProcessor

    @Autowired lateinit var identity: TestIdentityProvider

    @Autowired lateinit var admin: FirebaseAdmin

    @Autowired lateinit var transactions: TagTransactions

    @Autowired lateinit var users: UserService

    @Autowired lateinit var verifiedIdentity: FirebaseIdentity

    @BeforeEach fun setup() {
        listOf("users", "tags", "accountDeletions", "auditLog").forEach { template.remove(Query(), it) }
        identity.reset()
    }

    private fun job(): AccountDeletion {
        val id = UUID.randomUUID().toString()
        val user = User(id, id, "$id@example.com", UserStatus.DELETING, Instant.now(), Instant.now())
        template.insert(user)
        return template.insert(AccountDeletion(deletionKey(id), id, Instant.now()))
    }

    @Test fun `passes are bounded and old documents without scheduling fields remain processable`() {
        repeat(12) { job() }
        template.updateMulti(Query(), Update().unset("nextAttemptAt").unset("leaseUntil"), AccountDeletion::class.java)
        assertEquals(10, worker.dispatch())
        assertEquals(2, worker.dispatch())
        assertEquals(0, worker.dispatch())
        assertEquals(12, identity.deleteRequests.get())
        assertEquals(0, template.count(Query(), User::class.java))
    }

    @Test fun `an active lease prevents another worker and a stale worker cannot change a tag`() {
        val job = job()
        val tag =
            template.insert(
                Tag(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    TagOrigin.SELF_CREATED,
                    job.userId,
                    TagStatus.ACTIVE,
                    "Keys",
                    Instant.now(),
                    Instant.now(),
                ),
            )
        template.updateFirst(
            query(where("_id").`is`(job.id)),
            Update.update("leaseToken", "current").set("leaseUntil", Instant.now().plusSeconds(300)),
            AccountDeletion::class.java,
        )
        assertEquals(0, worker.dispatch())
        assertFailsWith<IllegalStateException> { processor.process(job, "stale") }
        assertEquals(TagStatus.ACTIVE, template.findById(tag.id, Tag::class.java)!!.status)
        assertEquals(0, identity.deleteRequests.get())
        template.updateFirst(query(where("_id").`is`(job.id)), Update.update("leaseUntil", Instant.EPOCH), AccountDeletion::class.java)
        assertEquals(1, worker.dispatch())
        assertNotNull(template.findById(job.id, AccountDeletion::class.java)!!.completedAt)
    }

    @Test fun `provider failures back off and a fresh worker completes without a user token`() {
        val job = job()
        identity.deleteStatus = 503
        assertEquals(1, worker.dispatch())
        val failed = template.findById(job.id, AccountDeletion::class.java)!!
        assertEquals(1, failed.failures)
        assertNotNull(failed.lastFailureAt)
        assertNull(failed.completedAt)
        assertEquals(0, worker.dispatch())
        identity.deleteStatus = 400 // Firebase account already gone after a previously lost response.
        template.updateFirst(query(where("_id").`is`(job.id)), Update.update("nextAttemptAt", Instant.EPOCH), AccountDeletion::class.java)
        assertEquals(1, AccountDeletionWorker(template, processor).dispatch())
        assertNotNull(template.findById(job.id, AccountDeletion::class.java)!!.completedAt)
        assertNull(template.findById(job.userId, User::class.java))
    }

    @Test fun `two workers do not delete the same Firebase account concurrently`() {
        val job = job()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        identity.beforeDelete = {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val first = pool.submit<Int> { worker.dispatch() }
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                assertEquals(0, AccountDeletionWorker(template, processor).dispatch())
            } finally {
                release.countDown()
            }
            assertEquals(1, first.get(10, TimeUnit.SECONDS))
        }
        assertEquals(1, identity.deleteRequests.get())
        assertNotNull(template.findById(job.id, AccountDeletion::class.java)!!.completedAt)
    }

    @Test fun `a mismatched Firebase identity is never deleted`() {
        val job = job()
        template.updateFirst(query(where("_id").`is`(job.userId)), Update.update("firebaseUserId", "different-identity"), User::class.java)
        assertEquals(1, worker.dispatch())
        assertEquals(0, identity.deleteRequests.get())
        assertNull(template.findById(job.id, AccountDeletion::class.java)!!.completedAt)
        assertEquals(1, template.findById(job.id, AccountDeletion::class.java)!!.failures)
    }

    @Test fun `no new deletion is accepted when the background job is unconfigured`() {
        val requests =
            AccountDeletionService(
                template,
                transactions,
                verifiedIdentity,
                users,
                AccountDeletionJobProperties(),
            )
        val jwt =
            Jwt
                .withTokenValue("verified-by-security-filter")
                .subject("test-user")
                .header("alg", "RS256")
                .claim("auth_time", Instant.now().epochSecond)
                .build()
        assertEquals(503, assertFailsWith<ResponseStatusException> { requests.request(jwt) }.statusCode.value())
        assertEquals(0, template.count(Query(), AccountDeletion::class.java))
        assertEquals(0, identity.userRequests.get())
    }

    @Test fun `the dispatch endpoint fails closed without job configuration or valid credentials`() {
        val auth = GoogleJobAuthentication()
        val missing = AccountDeletionJob(worker, AccountDeletionJobProperties(), auth)
        assertEquals(503, assertFailsWith<ResponseStatusException> { missing.dispatch(null) }.statusCode.value())
        val configured =
            AccountDeletionJob(
                worker,
                AccountDeletionJobProperties("https://example.test/delete", "deletion@example.iam.gserviceaccount.com"),
                auth,
            )
        for (header in listOf(null, "Basic invalid", "Bearer invalid")) {
            assertEquals(401, assertFailsWith<ResponseStatusException> { configured.dispatch(header) }.statusCode.value())
        }
    }
}
