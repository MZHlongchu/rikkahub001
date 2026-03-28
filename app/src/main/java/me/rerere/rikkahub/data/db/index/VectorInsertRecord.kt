package me.rerere.rikkahub.data.db.index

data class VectorInsertRecord(
    val chunkId: Long,
    val embeddingJson: String,
)
