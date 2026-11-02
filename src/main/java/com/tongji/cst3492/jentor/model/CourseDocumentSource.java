package com.tongji.cst3492.jentor.model;

/** One independently managed course PDF and its indexing state. */
public record CourseDocumentSource(
        String id,
        String path,
        String displayName,
        String contentHash,
        long size,
        long modifiedAt,
        int pageCount,
        int chunkCount,
        String status,
        String error,
        long indexedAt) {

    public static final String QUEUED = "QUEUED";
    public static final String INDEXING = "INDEXING";
    public static final String READY = "READY";
    public static final String MISSING = "MISSING";
    public static final String ERROR = "ERROR";

    public CourseDocumentSource {
        id = id == null ? "" : id;
        path = path == null ? "" : path;
        displayName = displayName == null ? "" : displayName;
        contentHash = contentHash == null ? "" : contentHash;
        status = status == null ? ERROR : status;
        error = error == null ? "" : error;
    }

    public boolean hasIndex() {
        return chunkCount > 0 && !ERROR.equals(status);
    }

    public CourseDocumentSource withStatus(String nextStatus, String nextError) {
        return new CourseDocumentSource(id, path, displayName, contentHash, size, modifiedAt,
                pageCount, chunkCount, nextStatus, nextError, indexedAt);
    }

    public CourseDocumentSource withParsed(
            String nextHash,
            long nextSize,
            long nextModifiedAt,
            int nextPageCount,
            int nextChunkCount,
            long nextIndexedAt) {
        return new CourseDocumentSource(id, path, displayName, nextHash, nextSize,
                nextModifiedAt, nextPageCount, nextChunkCount, READY, "", nextIndexedAt);
    }
}
