package com.tongji.cst3492.jentor.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class CourseIndex {
    private final int version;
    private final String embeddingModel;
    private final int dimensions;
    private final int chunkCount;
    private final float defaultIdf;
    private final Map<String, Float> idf;
    private final List<DocumentMeta> documents;
    private final List<Chunk> chunks;

    private CourseIndex(
            int version,
            String embeddingModel,
            int dimensions,
            int chunkCount,
            float defaultIdf,
            Map<String, Float> idf,
            List<DocumentMeta> documents,
            List<Chunk> chunks) {
        this.version = version;
        this.embeddingModel = embeddingModel;
        this.dimensions = dimensions;
        this.chunkCount = chunkCount;
        this.defaultIdf = defaultIdf;
        this.idf = Collections.unmodifiableMap(idf);
        this.documents = List.copyOf(documents);
        this.chunks = List.copyOf(chunks);
    }

    public static CourseIndex load(InputStream input) {
        try {
            JsonNode root = new ObjectMapper().readTree(input);
            int version = root.path("version").asInt(1);
            String embeddingModel = root.path("embeddingModel").asText("local");
            int dimensions = root.path("dimensions").asInt(512);
            int chunkCount = root.path("chunkCount").asInt(0);
            float defaultIdf = (float) root.path("defaultIdf").asDouble(1.0);

            Map<String, Float> idf = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> idfFields = root.path("idf").fields();
            while (idfFields.hasNext()) {
                Map.Entry<String, JsonNode> entry = idfFields.next();
                idf.put(entry.getKey(), (float) entry.getValue().asDouble());
            }

            List<DocumentMeta> documents = new ArrayList<>();
            for (JsonNode node : root.path("documents")) {
                documents.add(new DocumentMeta(
                        node.path("id").asText(),
                        node.path("title").asText(),
                        node.path("fileName").asText(),
                        node.path("pageCount").asInt()));
            }

            List<Chunk> chunks = new ArrayList<>();
            for (JsonNode node : root.path("chunks")) {
                JsonNode vectorNode = node.path("vector");
                if (vectorNode.size() != dimensions) {
                    throw new IllegalStateException(
                            "Chunk " + node.path("id").asText() + " has "
                                    + vectorNode.size() + " dimensions, expected " + dimensions);
                }
                float[] vector = new float[dimensions];
                for (int i = 0; i < dimensions; i++) {
                    vector[i] = (float) vectorNode.get(i).asDouble();
                }
                chunks.add(new Chunk(
                        node.path("id").asText(),
                        node.path("docId").asText(),
                        node.path("docTitle").asText(),
                        node.path("fileName").asText(),
                        node.path("locatorType").asText("PAGE"),
                        node.path("pageStart").asInt(),
                        node.path("pageEnd").asInt(),
                        node.path("chunkIndex").asInt(),
                        node.path("title").asText(""),
                        node.path("text").asText(""),
                        vector));
            }

            if (documents.isEmpty() || chunks.isEmpty()) {
                throw new IllegalStateException(
                        "Course index must contain at least one document and one chunk.");
            }
            for (Chunk chunk : chunks) {
                if (chunk.getFileName().isBlank()
                        || chunk.getPageStart() < 1
                        || chunk.getPageEnd() < chunk.getPageStart()) {
                    throw new IllegalStateException(
                            "Invalid chunk metadata for " + chunk.getId());
                }
            }
            if (chunkCount == 0) {
                chunkCount = chunks.size();
            }
            return new CourseIndex(
                    version, embeddingModel, dimensions, chunkCount, defaultIdf,
                    idf, documents, chunks);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read course index", e);
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
                // Nothing useful to do while closing a classpath resource.
            }
        }
    }

    public int getVersion() {
        return version;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public int getDimensions() {
        return dimensions;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public float getDefaultIdf() {
        return defaultIdf;
    }

    public Map<String, Float> getIdf() {
        return idf;
    }

    public List<DocumentMeta> getDocuments() {
        return documents;
    }

    public List<Chunk> getChunks() {
        return chunks;
    }
}
