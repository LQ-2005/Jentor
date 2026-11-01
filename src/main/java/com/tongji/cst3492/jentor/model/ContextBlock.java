package com.tongji.cst3492.jentor.model;

public record ContextBlock(Chunk chunk, String text, boolean truncated) {
    public int pageStart() {
        return chunk.getPageStart();
    }

    public int pageEnd() {
        return chunk.getPageEnd();
    }

    public String locator() {
        return chunk.locator();
    }
}
