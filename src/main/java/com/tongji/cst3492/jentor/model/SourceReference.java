package com.tongji.cst3492.jentor.model;

public record SourceReference(
        String fileName,
        String docTitle,
        String docId,
        int pageStart,
        int pageEnd,
        String locator,
        String excerpt,
        String chunkId) {
}
