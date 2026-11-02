package com.tongji.cst3492.jentor.model;

/** One independently managed course PDF and its indexing state. */
public record CourseDocumentSource(
        String id,
        String path,
        String displayName,
        String fileType,
        String contentHash,
        long size,
        long modifiedAt,
        int pageCount,
        int chunkCount,
        int tableCount,
        int imageCount,
        boolean ocrUsed,
        String parser,
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
        fileType = fileType == null ? "" : fileType;
        contentHash = contentHash == null ? "" : contentHash;
        parser = parser == null ? "" : parser;
        status = status == null ? ERROR : status;
        error = error == null ? "" : error;
    }

    public CourseDocumentSource(
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
        this(id, path, displayName, "", contentHash, size, modifiedAt, pageCount,
                chunkCount, 0, 0, false, "", status, error, indexedAt);
    }

    public boolean hasIndex() {
        return chunkCount > 0 && !ERROR.equals(status);
    }

    public CourseDocumentSource withStatus(String nextStatus, String nextError) {
        return new CourseDocumentSource(id, path, displayName, fileType, contentHash,
                size, modifiedAt, pageCount, chunkCount, tableCount, imageCount,
                ocrUsed, parser, nextStatus, nextError, indexedAt);
    }

    public CourseDocumentSource withParsed(
            String nextHash,
            long nextSize,
            long nextModifiedAt,
            int nextPageCount,
            int nextChunkCount,
            int nextTableCount,
            int nextImageCount,
            boolean nextOcrUsed,
            String nextParser,
            long nextIndexedAt) {
        return new CourseDocumentSource(id, path, displayName, fileType, nextHash,
                nextSize, nextModifiedAt, nextPageCount, nextChunkCount,
                nextTableCount, nextImageCount, nextOcrUsed, nextParser,
                READY, "", nextIndexedAt);
    }
}
