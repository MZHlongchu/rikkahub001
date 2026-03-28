package me.rerere.rikkahub.data.db.index.objectbox;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.HnswIndex;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.VectorDistanceType;

@Entity
public class VectorProbeObject {
    @Id
    public long id;

    @HnswIndex(dimensions = 3072, distanceType = VectorDistanceType.COSINE)
    public float[] embedding;

    public VectorProbeObject() {
        this.embedding = new float[3072];
    }

    public VectorProbeObject(float[] embedding) {
        this.embedding = embedding;
    }
}
