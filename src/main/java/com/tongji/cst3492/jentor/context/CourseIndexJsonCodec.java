package com.tongji.cst3492.jentor.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tongji.cst3492.jentor.model.Chunk;
import com.tongji.cst3492.jentor.model.CourseIndex;
import com.tongji.cst3492.jentor.model.DocumentMeta;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/** Writes the same page-aware JSON format consumed by {@link CourseIndex#load}. */
public final class CourseIndexJsonCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CourseIndexJsonCodec() {
    }

    public static byte[] toJsonBytes(CourseIndex index) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", index.getVersion());
        root.put("embeddingModel", index.getEmbeddingModel());
        root.put("dimensions", index.getDimensions());
        root.put("chunkCount", index.getChunkCount());
        root.put("defaultIdf", index.getDefaultIdf());

        ObjectNode idf = root.putObject("idf");
        for (Map.Entry<String, Float> entry : index.getIdf().entrySet()) {
            idf.put(entry.getKey(), entry.getValue());
        }

        ArrayNode documents = root.putArray("documents");
        for (DocumentMeta document : index.getDocuments()) {
            ObjectNode node = documents.addObject();
            node.put("id", document.getId());
            node.put("title", document.getTitle());
            node.put("fileName", document.getFileName());
            node.put("pageCount", document.getPageCount());
        }

        ArrayNode chunks = root.putArray("chunks");
        for (Chunk chunk : index.getChunks()) {
            ObjectNode node = chunks.addObject();
            node.put("id", chunk.getId());
            node.put("docId", chunk.getDocId());
            node.put("docTitle", chunk.getDocTitle());
            node.put("fileName", chunk.getFileName());
            node.put("locatorType", chunk.getLocatorType());
            node.put("pageStart", chunk.getPageStart());
            node.put("pageEnd", chunk.getPageEnd());
            node.put("chunkIndex", chunk.getChunkIndex());
            node.put("title", chunk.getTitle());
            node.put("text", chunk.getText());
            ArrayNode vector = node.putArray("vector");
            for (float value : chunk.getVector()) {
                vector.add(value);
            }
        }
        return MAPPER.writeValueAsBytes(root);
    }

    public static void write(CourseIndex index, OutputStream output) throws IOException {
        output.write(toJsonBytes(index));
    }
}
