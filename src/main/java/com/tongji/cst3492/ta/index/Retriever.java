package com.tongji.cst3492.ta.index;

import com.tongji.cst3492.ta.model.Chunk;
import com.tongji.cst3492.ta.model.CourseIndex;
import com.tongji.cst3492.ta.model.RetrievedChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BM25-first hybrid retrieval with a small hashed-vector component.
 *
 * <p>BM25 is the primary signal because the course corpus is small and questions
 * usually use terminology that appears verbatim in the slides. The vector score
 * acts as a tie-breaker, while an exact-title and phrase bonus protects precise
 * "what is X?" questions from being outranked by longer overview slides.</p>
 */
public final class Retriever {
    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final double VECTOR_WEIGHT = 0.15;
    private static final double BM25_WEIGHT = 0.85;
    private static final double TITLE_BONUS_WEIGHT = 0.75;
    private static final double DEFAULT_COVERAGE_THRESHOLD = 0.45;

    private final CourseIndex index;
    private final LocalEmbedder embedder;
    private final List<ChunkStats> stats;
    private final Map<String, Integer> documentFrequency;
    private final double averageLength;

    public Retriever(CourseIndex index) {
        this.index = index;
        this.embedder = new LocalEmbedder(index);
        this.stats = new ArrayList<>();
        this.documentFrequency = new HashMap<>();

        long totalLength = 0;
        for (Chunk chunk : index.getChunks()) {
            ChunkStats chunkStats = new ChunkStats(chunk);
            stats.add(chunkStats);
            totalLength += chunkStats.length;
            for (String token : chunkStats.termFrequency.keySet()) {
                documentFrequency.merge(token, 1, Integer::sum);
            }
        }
        this.averageLength = stats.isEmpty() ? 0.0 : (double) totalLength / stats.size();
    }

    public List<RetrievedChunk> retrieve(String query, int topK) {
        if (stats.isEmpty()) {
            return List.of();
        }
        List<String> queryTokens = Tokens.tokenize(query);
        float[] queryVector = embedder.embed(query);
        Set<String> queryTermSet = new HashSet<>(queryTokens);

        double[] vectorScores = new double[stats.size()];
        double[] lexicalScores = new double[stats.size()];
        for (int i = 0; i < stats.size(); i++) {
            ChunkStats chunkStats = stats.get(i);
            vectorScores[i] = LocalEmbedder.cosine(queryVector, chunkStats.chunk.getVector());
            lexicalScores[i] = bm25(queryTokens, chunkStats);
        }

        double[] normalizedVector = zScore(vectorScores);
        double[] normalizedLexical = zScore(lexicalScores);
        double[] fused = new double[stats.size()];
        for (int i = 0; i < stats.size(); i++) {
            fused[i] = VECTOR_WEIGHT * normalizedVector[i]
                    + BM25_WEIGHT * normalizedLexical[i]
                    + TITLE_BONUS_WEIGHT * titleAndPhraseBonus(queryTokens, stats.get(i));
        }

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < stats.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble((Integer i) -> fused[i]).reversed());

        int limit = Math.max(0, Math.min(topK, order.size()));
        List<RetrievedChunk> results = new ArrayList<>(limit);
        for (int position = 0; position < limit; position++) {
            int i = order.get(position);
            ChunkStats chunkStats = stats.get(i);
            results.add(new RetrievedChunk(
                    chunkStats.chunk,
                    vectorScores[i],
                    lexicalScores[i],
                    fused[i],
                    coverage(queryTermSet, chunkStats)));
        }
        return results;
    }

    public boolean isGrounded(List<RetrievedChunk> results) {
        return isGrounded(results, DEFAULT_COVERAGE_THRESHOLD);
    }

    public boolean isGrounded(List<RetrievedChunk> results, double threshold) {
        return results != null
                && !results.isEmpty()
                && results.get(0).getCoverage() >= threshold;
    }

    public CourseIndex getIndex() {
        return index;
    }

    public LocalEmbedder getEmbedder() {
        return embedder;
    }

    private double bm25(List<String> queryTokens, ChunkStats chunkStats) {
        double score = 0.0;
        int total = stats.size();
        for (String token : queryTokens) {
            int tf = chunkStats.termFrequency.getOrDefault(token, 0);
            if (tf == 0) {
                continue;
            }
            int df = documentFrequency.getOrDefault(token, 0);
            double idf = Math.log(1.0 + (total - df + 0.5) / (df + 0.5));
            double denominator = tf + K1 * (1.0 - B + B * chunkStats.length / averageLength);
            score += idf * tf * (K1 + 1.0) / denominator;
        }
        return score;
    }

    private double titleAndPhraseBonus(List<String> queryTokens, ChunkStats chunkStats) {
        double score = 0.0;
        if (chunkStats.titleTokens.equals(queryTokens)) {
            score += 1.0;
        } else if (chunkStats.titleTokenSet.containsAll(queryTokens)) {
            score += 0.6;
        }
        for (int i = 0; i + 1 < queryTokens.size(); i++) {
            String key = Tokens.bigramKey(queryTokens.get(i), queryTokens.get(i + 1));
            if (chunkStats.titleBigrams.contains(key)) {
                score += 0.4;
            } else if (chunkStats.textBigrams.contains(key)) {
                score += 0.2;
            }
        }
        if (queryTokens.size() == 1) {
            String token = queryTokens.get(0);
            if (chunkStats.titleTokenSet.contains(token)) {
                score += 0.5;
            } else if (chunkStats.textTokenSet.contains(token)) {
                score += 0.25;
            }
        }
        return score;
    }

    private double coverage(Set<String> queryTerms, ChunkStats chunkStats) {
        double totalWeight = 0.0;
        double matchedWeight = 0.0;
        for (String term : queryTerms) {
            if (containsCjk(term)) {
                continue;
            }
            double weight = index.getIdf().getOrDefault(term, index.getDefaultIdf());
            totalWeight += weight;
            if (chunkStats.documentTokenSet.contains(term)) {
                matchedWeight += weight;
            }
        }
        return totalWeight == 0.0 ? 0.0 : matchedWeight / totalWeight;
    }

    private boolean containsCjk(String term) {
        for (int i = 0; i < term.length(); i++) {
            if (Character.UnicodeScript.of(term.charAt(i))
                    == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private static double[] zScore(double[] values) {
        double mean = 0.0;
        for (double value : values) {
            mean += value;
        }
        mean /= Math.max(1, values.length);

        double variance = 0.0;
        for (double value : values) {
            double difference = value - mean;
            variance += difference * difference;
        }
        variance /= Math.max(1, values.length);
        double deviation = Math.sqrt(variance);
        if (deviation == 0.0) {
            deviation = 1.0;
        }

        double[] normalized = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            normalized[i] = (values[i] - mean) / deviation;
        }
        return normalized;
    }

    private static final class ChunkStats {
        private final Chunk chunk;
        private final Map<String, Integer> termFrequency;
        private final int length;
        private final List<String> titleTokens;
        private final Set<String> titleTokenSet;
        private final Set<String> textTokenSet;
        private final Set<String> documentTokenSet;
        private final Set<String> titleBigrams;
        private final Set<String> textBigrams;

        private ChunkStats(Chunk chunk) {
            this.chunk = chunk;
            this.titleTokens = Tokens.tokenize(chunk.getTitle());
            this.titleTokenSet = new HashSet<>(titleTokens);
            this.textTokenSet = Tokens.tokenSet(chunk.getText());
            this.documentTokenSet = new HashSet<>(titleTokenSet);
            this.documentTokenSet.addAll(textTokenSet);
            this.titleBigrams = new HashSet<>(Tokens.bigramKeys(titleTokens));
            this.textBigrams = new HashSet<>(Tokens.bigramKeys(Tokens.tokenize(chunk.getText())));

            List<String> searchTokens = Tokens.tokenize(
                    chunk.getTitle() + "\n" + chunk.getTitle() + "\n" + chunk.getText());
            this.termFrequency = new HashMap<>();
            for (String token : searchTokens) {
                termFrequency.merge(token, 1, Integer::sum);
            }
            this.length = searchTokens.size();
        }
    }
}
