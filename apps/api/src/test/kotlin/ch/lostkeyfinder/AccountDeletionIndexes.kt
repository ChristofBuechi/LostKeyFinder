package ch.lostkeyfinder

import ch.lostkeyfinder.users.AccountDeletion
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index

// Operator-only provisioning, never called by the runtime worker.
fun provisionAccountDeletionIndexes(template: MongoTemplate) {
    template
        .indexOps(
            AccountDeletion::class.java,
        ).createIndex(Index().on("completedAt", Sort.Direction.ASC).on("createdAt", Sort.Direction.ASC))
}
