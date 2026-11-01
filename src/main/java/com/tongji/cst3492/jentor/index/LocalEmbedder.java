package com.tongji.cst3492.jentor.index;

import com.tongji.cst3492.jentor.model.CourseIndex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic hashed TF-IDF embedding used when no embedding service is configured.
 * The same algorithm is implemented in tools/build_index.py.
 */
public final class LocalEmbedder {
    private final int dimensions;
    private final Map<String, Float> idf;
    private final float defaultIdf;

    public LocalEmbedder(CourseIndex index) {
        this.dimensions = index.getDimensions();
        this.idf = index.getIdf();
        this.defaultIdf = index.getDefaultIdf();
    }

    public float[] embed(String text) {
        List<String> tokens = Tokens.tokenize(text);
        Map<String, Integer> counts = new HashMap<>();
        for (String token : tokens) {
            counts.merge(token, 1, Integer::sum);
        }

        float[] vector = new float[dimensions];
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String token = entry.getKey();
            int count = entry.getValue();
            Hash hash = hash(token);
            double weight = (1.0 + Math.log(count)) * idf.getOrDefault(token, defaultIdf);
            vector[hash.index()] += (float) (hash.sign() * weight);
        }

        double norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm > 0.0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        return vector;
    }

    public static double cosine(float[] left, float[] right) {
        double score = 0.0;
        int length = Math.min(left.length, right.length);
        for (int i = 0; i < length; i++) {
            score += left[i] * right[i];
        }
        return score;
    }

    private Hash hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (bytes[i] & 0xFFL);
            }
            int index = (int) Long.remainderUnsigned(value, dimensions);
            double sign = ((value >>> 63) & 1L) == 0L ? 1.0 : -1.0;
            return new Hash(index, sign);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
        }
    }

    private record Hash(int index, double sign) {
    }
}
