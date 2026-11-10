package com.tongji.cst3492.jentor.tools;

import com.tongji.cst3492.jentor.index.Retriever;
import com.tongji.cst3492.jentor.index.Tokens;
import com.tongji.cst3492.jentor.llm.Answer;
import com.tongji.cst3492.jentor.llm.AnswerService;
import com.tongji.cst3492.jentor.llm.LlmConfig;
import com.tongji.cst3492.jentor.llm.MockChatClient;
import com.tongji.cst3492.jentor.llm.CancellationToken;
import com.tongji.cst3492.jentor.model.CodeContext;
import com.tongji.cst3492.jentor.model.CourseIndex;
import com.tongji.cst3492.jentor.model.RetrievedChunk;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class RetrievalHarness {
    private record Query(String text, String expectedFile, int expectedPage) {
    }

    private static final List<Query> QUERIES = List.of(
            new Query("What is the difference between an abstract class and an interface?",
                    "Lec-2-OOP-with-Java.pdf", 30),
            new Query("What is a checked exception?", "Lec-3.1-Exceptions.pdf", 5),
            new Query("What is a generic type?", "Lec-3.2-Generics-and-Collections.pdf", 5),
            new Query("What does the @Override annotation do?",
                    "Lec-3.3-ModernSyntax-Annotations-and-Reflection.pdf", 6),
            new Query("How do I write a JUnit test?", "Lec-3.4-JUnit.pdf", 3),
            new Query("What is passing by value?", "Lec-2-OOP-with-Java.pdf", 5),
            new Query("What is an array?", "Lec-1-Java-Fundamentals.pdf", 20),
            new Query("What is BM25?",
                    "Lec-5-Building-AI-Powered-Java-Applications.pdf", 24),
            new Query("What is the project layout of the gradebook example?",
                    "Lec-5-Building-AI-Powered-Java-Applications.pdf", 10),
            new Query("What is a constructor?", "Lec-2-OOP-with-Java.pdf", 6)
    );

    public static void main(String[] args) throws Exception {
        System.out.println("TOKENS abstract: "
                + Tokens.tokenize("What is the difference between an abstract class and an interface?"));
        System.out.println("TOKENS generic: " + Tokens.tokenize("What is a generic type?"));
        System.out.println("TOKENS junit: " + Tokens.tokenize("How do I write a JUnit test?"));

        Path indexPath = Path.of(
                "src", "main", "resources", "index", "course-index.json");
        if (!Files.isRegularFile(indexPath)) {
            throw new IllegalStateException(
                    "Run this harness from the project root; missing " + indexPath);
        }
        CourseIndex index;
        try (InputStream input = Files.newInputStream(indexPath)) {
            index = CourseIndex.load(input);
        }
        System.out.println("INDEX docs=" + index.getDocuments().size()
                + " chunks=" + index.getChunks().size()
                + " dims=" + index.getDimensions());

        Retriever retriever = new Retriever(index);
        int hits = 0;
        double reciprocalRank = 0.0;
        for (Query query : QUERIES) {
            List<RetrievedChunk> results = retriever.retrieve(query.text(), 3);
            boolean hit = results.stream().anyMatch(result ->
                    result.getChunk().getFileName().equals(query.expectedFile())
                            && result.getChunk().getPageStart() <= query.expectedPage()
                            && query.expectedPage() <= result.getChunk().getPageEnd());
            hits += hit ? 1 : 0;
            for (int i = 0; i < results.size(); i++) {
                RetrievedChunk result = results.get(i);
                if (result.getChunk().getFileName().equals(query.expectedFile())
                        && result.getChunk().getPageStart() <= query.expectedPage()
                        && query.expectedPage() <= result.getChunk().getPageEnd()) {
                    reciprocalRank += 1.0 / (i + 1);
                    break;
                }
            }
            System.out.printf(
                    "Q=%s | hit=%s | cov=%.2f%n",
                    query.text(), hit, results.get(0).getCoverage());
            for (RetrievedChunk result : results) {
                System.out.printf(
                        "   %s %s | cos=%.3f | bm25=%.3f | %s%n",
                        result.getChunk().getFileName(),
                        result.getChunk().locator(),
                        result.getVectorScore(),
                        result.getBm25Score(),
                        result.getChunk().getText().substring(
                                0, Math.min(60, result.getChunk().getText().length()))
                                .replace('\n', ' '));
            }
        }
        System.out.printf("Hit@3=%d/%d MRR=%.3f%n", hits, QUERIES.size(),
                reciprocalRank / QUERIES.size());

        LlmConfig mockConfig = new LlmConfig("mock", "mock", "mock", 0.0, 256);
        Answer answer = new AnswerService(retriever, new MockChatClient())
                .answer(QUERIES.get(0).text(), CodeContext.empty(),
                        mockConfig, 5, 0.45, 6000, "en", List.of(),
                        new CancellationToken(), null);
        System.out.println("MOCK grounded=" + answer.grounded());
        System.out.println("MOCK citationCount="
                + new com.tongji.cst3492.jentor.llm.CitationValidator()
                .parse(answer.text()).size());
        System.out.println("MOCK warnings=" + answer.warnings());

        Answer general = new AnswerService(retriever, new MockChatClient())
                .answer(
                        "What is the weather in Shanghai today?",
                        CodeContext.empty(), mockConfig, 5, 0.45, 6000, "en", List.of(),
                        new CancellationToken(), null);
        System.out.println("GENERAL grounded=" + general.grounded());
        System.out.println("GENERAL fallbackNote="
                + com.tongji.cst3492.jentor.llm.PromptBuilder.FALLBACK_SENTENCE
                .equals(general.disclosure()));
    }
}
