package me.rerere.rikkahub.data.db.index.objectbox;

public class ObjectBoxVectorRecord {
    public final long chunkId;
    public final int embeddingDimension;
    public final float[] embedding;

    public ObjectBoxVectorRecord(long chunkId, int embeddingDimension, float[] embedding) {
        this.chunkId = chunkId;
        this.embeddingDimension = embeddingDimension;
        this.embedding = embedding;
    }
}
