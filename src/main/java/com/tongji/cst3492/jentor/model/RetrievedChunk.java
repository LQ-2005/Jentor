package com.tongji.cst3492.jentor.model;

public final class RetrievedChunk {
    private final Chunk chunk;
    private final double vectorScore;
    private final double bm25Score;
    private final double fusedScore;
    private final double coverage;

    public RetrievedChunk(
            Chunk chunk,
            double vectorScore,
            double bm25Score,
            double fusedScore,
            double coverage) {
        this.chunk = chunk;
        this.vectorScore = vectorScore;
        this.bm25Score = bm25Score;
        this.fusedScore = fusedScore;
        this.coverage = coverage;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public double getVectorScore() {
        return vectorScore;
    }

    public double getBm25Score() {
        return bm25Score;
    }

    public double getFusedScore() {
        return fusedScore;
    }

    public double getCoverage() {
        return coverage;
    }
}
