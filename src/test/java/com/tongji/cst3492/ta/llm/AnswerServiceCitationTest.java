package com.tongji.cst3492.ta.llm;

import com.tongji.cst3492.ta.index.Retriever;
import com.tongji.cst3492.ta.model.CodeContext;
import com.tongji.cst3492.ta.model.CourseIndex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerServiceCitationTest {
    private static final String QUESTION = "give me an example about java interface";
    private static final LlmConfig CONFIG = new LlmConfig("mock", "mock", "mock", 0.0, 256);
    private static Retriever retriever;

    @BeforeAll
    static void loadIndex() throws IOException {
        try (InputStream input = AnswerServiceCitationTest.class.getResourceAsStream(
                "/index/course-index.json")) {
            assertNotNull(input);
            retriever = new Retriever(CourseIndex.load(input));
        }
    }

    @Test
    void anEnglishInterfaceExampleDoesNotRetryBecauseOfWordsInsideCode() throws IOException {
        ExampleClient client = new ExampleClient(false, false);
        Answer answer = ask(client);

        assertTrue(answer.grounded());
        assertEquals(CitationStatus.VALIDATED, answer.citationStatus());
        assertEquals(1, client.calls);
        assertEquals(1, answer.sources().size());
        assertTrue(answer.warnings().isEmpty());
    }

    @Test
    void aGeneralSectionViolationGetsSpecificRepairInstructions() throws IOException {
        ExampleClient client = new ExampleClient(true, false);
        Answer answer = ask(client);

        assertEquals(2, client.calls);
        assertTrue(client.retrySystem.contains("CITATION_IN_GENERAL_SECTION means"));
        assertTrue(client.retrySystem.contains("Never invent a document name, page number"));
        assertTrue(client.retrySystem.contains("Move course-supported statements AND their citations"));
        assertEquals(CitationStatus.VALIDATED, answer.citationStatus());
        assertFalse(answer.sources().isEmpty());
        assertTrue(answer.warnings().isEmpty());
    }

    @Test
    void aRepeatedRealViolationStillFailsValidation() throws IOException {
        ExampleClient client = new ExampleClient(true, true);
        Answer answer = ask(client);

        assertEquals(2, client.calls);
        assertEquals(CitationStatus.FAILED, answer.citationStatus());
        assertTrue(answer.warnings().stream()
                .anyMatch(warning -> warning.contains("CITATION_IN_GENERAL_SECTION")));
    }

    private static Answer ask(ChatClient client) throws IOException {
        return new AnswerService(retriever, client).answer(QUESTION, CodeContext.empty(),
                CONFIG, 5, 0.45, 6000, "en", List.of(), new CancellationToken(), null);
    }

    private static final class ExampleClient implements ChatClient {
        private final boolean firstViolation;
        private final boolean repeatViolation;
        private int calls;
        private String retrySystem;

        private ExampleClient(boolean firstViolation, boolean repeatViolation) {
            this.firstViolation = firstViolation;
            this.repeatViolation = repeatViolation;
        }

        @Override
        public String complete(LlmConfig config, List<ChatMessage> messages, CancellationToken token) {
            calls++;
            if (calls == 2) {
                retrySystem = messages.getFirst().content();
            }
            String mock = new MockChatClient().complete(config, messages, token);
            String citation = new CitationValidator().parse(mock).getFirst().raw();
            boolean violate = firstViolation && (calls == 1 || repeatViolation);
            String example = """
                    ```java
                    interface Greeting { String message(); }
                    // The section label is only example data, not an answer heading.
                    String label = "General supplement";
                    ```
                    """;
            if (!firstViolation) {
                return "# Java Interface Example\n## Course basis\n"
                        + "An interface defines a contract. " + citation
                        + "\n## Example\n" + example
                        + "This explanation refers to the course. " + citation;
            }
            return "## Course basis\nAn interface defines a contract. " + citation
                    + "\n## General supplement\n" + example
                    + "This is an original example." + (violate ? " " + citation : "");
        }
    }
}
