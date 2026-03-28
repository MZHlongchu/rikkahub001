package me.rerere.rikkahub.data.db.index.objectbox;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.HnswIndex;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import io.objectbox.annotation.Unique;
import io.objectbox.annotation.VectorDistanceType;

@Entity
public class MemoryVectorObject {
    @Id
    public long id;

    @Unique
    public long chunkId;

    @Index
    public int embeddingDimension;

    @HnswIndex(dimensions = 3072, distanceType = VectorDistanceType.COSINE)
    public float[] embedding;

    public MemoryVectorObject() {
        this.embedding = new float[3072];
    }

    public MemoryVectorObject(long chunkId, int embeddingDimension, float[] embedding) {
        this.chunkId = chunkId;
        this.embeddingDimension = embeddingDimension;
        this.embedding = embedding;
    }
}
