package ch.lostkeyfinder.tags

import com.mongodb.client.MongoClient
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component

interface TagTransactions {
    fun <T : Any> run(action: (MongoOperations) -> T): T
}

@Component
class MongoTagTransactions(
    private val client: MongoClient,
    private val template: MongoTemplate,
) : TagTransactions {
    override fun <T : Any> run(action: (MongoOperations) -> T): T =
        client.startSession().use { session ->
            session.withTransaction<T> {
                requireNotNull(template.withSession { session }.execute<T> { action(it) })
            }
        }
}
