package ch.lostkeyfinder

import ch.lostkeyfinder.chat.Chat
import ch.lostkeyfinder.chat.ChatImage
import ch.lostkeyfinder.chat.ChatMessage
import ch.lostkeyfinder.chat.ChatNotice
import ch.lostkeyfinder.chat.FinderDraft
import ch.lostkeyfinder.chat.LossCase
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import java.time.Duration

// Operator/test provisioning only. The runtime identity never creates indexes.
fun provisionChatIndexes(template: MongoTemplate) {
    template.indexOps(LossCase::class.java).createIndex(Index().on("activeKey", Sort.Direction.ASC).unique().sparse())
    template.indexOps(LossCase::class.java).createIndex(Index().on("tagId", Sort.Direction.ASC).on("closedAt", Sort.Direction.ASC))
    template
        .indexOps(
            Chat::class.java,
        ).createIndex(Index().on("caseId", Sort.Direction.ASC).on("finderUserId", Sort.Direction.ASC).unique())
    template
        .indexOps(
            Chat::class.java,
        ).createIndex(
            Index().on("participantIds", Sort.Direction.ASC).on("lastMessageAt", Sort.Direction.DESC).on("_id", Sort.Direction.DESC),
        )
    template
        .indexOps(
            Chat::class.java,
        ).createIndex(Index().on("tagId", Sort.Direction.ASC).on("lastMessageAt", Sort.Direction.DESC).on("_id", Sort.Direction.DESC))
    template.indexOps(Chat::class.java).createIndex(Index().on("tagId", Sort.Direction.ASC).on("closedAt", Sort.Direction.ASC))
    template
        .indexOps(
            ChatMessage::class.java,
        ).createIndex(Index().on("chatId", Sort.Direction.ASC).on("sequence", Sort.Direction.DESC).unique())
    template
        .indexOps(
            ChatNotice::class.java,
        ).createIndex(Index().on("status", Sort.Direction.ASC).on("dueAt", Sort.Direction.ASC).on("leaseUntil", Sort.Direction.ASC))
    template.indexOps(FinderDraft::class.java).createIndex(Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO))
    template.indexOps(ChatImage::class.java).createIndex(Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO))
}
