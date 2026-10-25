package ch.lostkeyfinder

import ch.lostkeyfinder.tags.TagTransactions
import de.bwaldvogel.mongo.MongoServer
import de.bwaldvogel.mongo.backend.memory.MemoryBackend
import de.bwaldvogel.mongo.bson.Document
import io.netty.channel.Channel
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.DynamicPropertyRegistrar

@TestConfiguration(proxyBeanMethods = false)
@Profile("ci")
class InMemoryMongoConfiguration {
    // Offline contract tests only. Real rollback/isolation is proved by Firestore integration tests.
    @Bean
    @Primary
    fun offlineTagTransactions(template: MongoTemplate): TagTransactions =
        object : TagTransactions {
            override fun <T : Any> run(action: (MongoOperations) -> T): T = synchronized(this) { action(template) }
        }

    @Bean(destroyMethod = "shutdown")
    fun mongoServer(): MongoServer =
        MongoServer(HealthCompatibleMemoryBackend()).apply {
            bind("127.0.0.1", 0)
        }

    @Bean
    fun mongoProperties(server: MongoServer) =
        DynamicPropertyRegistrar { registry ->
            registry.add("spring.mongodb.uri") {
                "${server.connectionString}/foundation?directConnection=true&retryWrites=false&serverSelectionTimeoutMS=3000"
            }
        }
}

// mongo-java-server 1.47 implements the legacy handshake but not its newer name.
// Keep Spring Boot's actual MongoHealthIndicator unchanged for this test context.
private class HealthCompatibleMemoryBackend : MemoryBackend() {
    override fun handleCommand(
        channel: Channel,
        databaseName: String,
        command: String,
        query: Document,
    ): Document = super.handleCommand(channel, databaseName, if (command.equals("hello", ignoreCase = true)) "ismaster" else command, query)
}
