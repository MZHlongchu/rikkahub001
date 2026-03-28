package me.rerere.rikkahub.data.db.index.objectbox;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.ObjectWithScore;
import io.objectbox.query.Query;

public class RawIndexObjectBoxVectorStore {
    private static final String STORE_DIRECTORY = "rikka_hub_objectbox_index";
    private static final int MAX_VECTOR_DIMENSIONS = 3072;
    private static final int PROBE_DIMENSION = 2;

    private final Context context;
    private BoxStore boxStore;

    public RawIndexObjectBoxVectorStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized String getDatabasePath() {
        return new File(context.getFilesDir(), STORE_DIRECTORY).getAbsolutePath();
    }

    public synchronized void ensureReady() {
        getOrOpenStore();
    }

    public synchronized void putKnowledgeBaseVectors(List<ObjectBoxVectorRecord> records) {
        if (records.isEmpty()) return;
        Box<KnowledgeBaseVectorObject> box = getOrOpenStore().boxFor(KnowledgeBaseVectorObject.class);
        List<KnowledgeBaseVectorObject> objects = new ArrayList<>(records.size());
        for (ObjectBoxVectorRecord record : records) {
            objects.add(new KnowledgeBaseVectorObject(
                record.chunkId,
                record.embeddingDimension,
                padVector(record.embedding, record.embeddingDimension)
            ));
        }
        box.put(objects);
    }

    public synchronized void putMemoryVectors(List<ObjectBoxVectorRecord> records) {
        if (records.isEmpty()) return;
        Box<MemoryVectorObject> box = getOrOpenStore().boxFor(MemoryVectorObject.class);
        List<MemoryVectorObject> objects = new ArrayList<>(records.size());
        for (ObjectBoxVectorRecord record : records) {
            objects.add(new MemoryVectorObject(
                record.chunkId,
                record.embeddingDimension,
                padVector(record.embedding, record.embeddingDimension)
            ));
        }
        box.put(objects);
    }

    public synchronized Map<Long, Double> searchKnowledgeBaseDistances(
        List<Long> candidateIds,
        float[] queryEmbedding,
        int dimension,
        int limit
    ) {
        Box<KnowledgeBaseVectorObject> box = getOrOpenStore().boxFor(KnowledgeBaseVectorObject.class);
        Query<KnowledgeBaseVectorObject> query = box.query(
            KnowledgeBaseVectorObject_.embedding.nearestNeighbors(padVector(queryEmbedding, dimension), limit)
                .and(KnowledgeBaseVectorObject_.embeddingDimension.equal(dimension))
                .and(KnowledgeBaseVectorObject_.chunkId.oneOf(toLongArray(candidateIds)))
        ).build();
        try {
            LinkedHashMap<Long, Double> result = new LinkedHashMap<>();
            for (ObjectWithScore<KnowledgeBaseVectorObject> scored : query.findWithScores()) {
                result.put(scored.get().chunkId, scored.getScore());
            }
            return result;
        } finally {
            query.close();
        }
    }

    public synchronized Map<Long, Double> searchMemoryDistances(
        List<Long> candidateIds,
        float[] queryEmbedding,
        int dimension,
        int limit
    ) {
        Box<MemoryVectorObject> box = getOrOpenStore().boxFor(MemoryVectorObject.class);
        Query<MemoryVectorObject> query = box.query(
            MemoryVectorObject_.embedding.nearestNeighbors(padVector(queryEmbedding, dimension), limit)
                .and(MemoryVectorObject_.embeddingDimension.equal(dimension))
                .and(MemoryVectorObject_.chunkId.oneOf(toLongArray(candidateIds)))
        ).build();
        try {
            LinkedHashMap<Long, Double> result = new LinkedHashMap<>();
            for (ObjectWithScore<MemoryVectorObject> scored : query.findWithScores()) {
                result.put(scored.get().chunkId, scored.getScore());
            }
            return result;
        } finally {
            query.close();
        }
    }

    public synchronized void deleteKnowledgeBaseVectors(int dimension, List<Long> chunkIds) {
        if (chunkIds.isEmpty()) return;
        Box<KnowledgeBaseVectorObject> box = getOrOpenStore().boxFor(KnowledgeBaseVectorObject.class);
        Query<KnowledgeBaseVectorObject> query = box.query(
            KnowledgeBaseVectorObject_.embeddingDimension.equal(dimension)
                .and(KnowledgeBaseVectorObject_.chunkId.oneOf(toLongArray(chunkIds)))
        ).build();
        try {
            query.remove();
        } finally {
            query.close();
        }
    }

    public synchronized void deleteMemoryVectors(int dimension, List<Long> chunkIds) {
        if (chunkIds.isEmpty()) return;
        Box<MemoryVectorObject> box = getOrOpenStore().boxFor(MemoryVectorObject.class);
        Query<MemoryVectorObject> query = box.query(
            MemoryVectorObject_.embeddingDimension.equal(dimension)
                .and(MemoryVectorObject_.chunkId.oneOf(toLongArray(chunkIds)))
        ).build();
        try {
            query.remove();
        } finally {
            query.close();
        }
    }

    public synchronized void clearAllVectors() {
        BoxStore store = getOrOpenStore();
        store.boxFor(KnowledgeBaseVectorObject.class).removeAll();
        store.boxFor(MemoryVectorObject.class).removeAll();
    }

    public synchronized void runProbe() {
        Box<VectorProbeObject> box = getOrOpenStore().boxFor(VectorProbeObject.class);
        List<VectorProbeObject> objects = new ArrayList<>(2);
        objects.add(new VectorProbeObject(padVector(new float[] { 1f, 0f }, PROBE_DIMENSION)));
        objects.add(new VectorProbeObject(padVector(new float[] { 0f, 1f }, PROBE_DIMENSION)));
        box.put(objects);
        try {
            Query<VectorProbeObject> query = box.query(
                VectorProbeObject_.embedding.nearestNeighbors(padVector(new float[] { 1f, 0f }, PROBE_DIMENSION), 2)
            ).build();
            try {
                List<ObjectWithScore<VectorProbeObject>> results = query.findWithScores();
                if (results.isEmpty()) {
                    throw new IllegalStateException("ObjectBox probe returned no rows");
                }
                double topScore = results.get(0).getScore();
                if (!Double.isFinite(topScore)) {
                    throw new IllegalStateException("ObjectBox probe returned non-finite score=" + topScore);
                }
            } finally {
                query.close();
            }
        } finally {
            box.removeAll();
        }
    }

    public synchronized void close() {
        if (boxStore != null && !boxStore.isClosed()) {
            boxStore.close();
        }
        boxStore = null;
    }

    private BoxStore getOrOpenStore() {
        if (boxStore != null && !boxStore.isClosed()) {
            return boxStore;
        }
        File directory = new File(context.getFilesDir(), STORE_DIRECTORY);
        if (!directory.exists()) {
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
        }
        boxStore = MyObjectBox.builder()
            .androidContext(context)
            .directory(directory)
            .build();
        return boxStore;
    }

    private static float[] padVector(float[] embedding, int dimension) {
        if (dimension <= 0 || dimension > MAX_VECTOR_DIMENSIONS) {
            throw new IllegalArgumentException(
                "Vector dimension " + dimension + " exceeds ObjectBox HNSW limit " + MAX_VECTOR_DIMENSIONS
            );
        }
        if (embedding.length != dimension) {
            throw new IllegalStateException(
                "Vector size mismatch: expected=" + dimension + " actual=" + embedding.length
            );
        }
        float[] padded = new float[MAX_VECTOR_DIMENSIONS];
        System.arraycopy(embedding, 0, padded, 0, dimension);
        return padded;
    }

    private static long[] toLongArray(List<Long> values) {
        long[] result = new long[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }
}
