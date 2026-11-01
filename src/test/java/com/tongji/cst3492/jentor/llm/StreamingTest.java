package com.tongji.cst3492.jentor.llm;

import com.tongji.cst3492.jentor.index.Retriever;
import com.tongji.cst3492.jentor.model.CodeContext;
import com.tongji.cst3492.jentor.model.CourseIndex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingTest {
    private static Retriever retriever;

    @BeforeAll
    static void loadIndex() throws IOException {
        try (InputStream input = StreamingTest.class.getResourceAsStream(
                "/index/course-index.json")) {
            assertNotNull(input, "Bundled index must be present");
            retriever = new Retriever(CourseIndex.load(input));
        }
    }

    @Test
    void streamChunksExposeOnlyTheirText() throws IOException {
        assertNull(OpenAiCompatibleClient.deltaContent(
                "{\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}"));
        assertNull(OpenAiCompatibleClient.deltaContent("not-json"));
        assertEquals("Hello", OpenAiCompatibleClient.deltaContent(
                "{\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}"));
        assertEquals(" world", OpenAiCompatibleClient.deltaContent(
                "{\"choices\":[{\"message\":{\"content\":\" world\"}}]}"));
        assertThrows(IOException.class, () -> OpenAiCompatibleClient.deltaContent(
                "{\"error\":{\"message\":\"rate limited\"}}"));
    }

    @Test
    void mockClientStreamsTheWholeAnswer() {
        List<String> deltas = new ArrayList<>();
        LlmConfig config = new LlmConfig("mock", "mock", "mock", 0.0, 256);
        List<ChatMessage> messages = List.of(
                ChatMessage.system("Answer in Simplified Chinese."),
                ChatMessage.user("question"));

        String text = new MockChatClient()
                .stream(config, messages, new CancellationToken(), deltas::add);

        assertEquals(text, String.join("", deltas));
        assertTrue(deltas.size() > 1, "Mock streaming should emit more than one chunk");
    }

    @Test
    void answerServiceReportsDeltasAndKeepsTheFinalText() throws IOException {
        List<String> deltas = new ArrayList<>();
        Answer answer = new AnswerService(retriever, new MockChatClient())
                .answer(
                        "What is an array?",
                        CodeContext.empty(),
                        new LlmConfig("mock", "mock", "mock", 0.0, 256),
                        5, 0.45, 6000, "en", List.of(),
                        new CancellationToken(),
                        null,
                        deltas::add);

        assertEquals(answer.text(), String.join("", deltas));
        assertEquals(CitationStatus.VALIDATED, answer.citationStatus());
        assertTrue(answer.warnings().isEmpty());
    }

    @Test
    void citationRetryResetsTheStreamedText() throws IOException {
        StringBuilder visible = new StringBuilder();
        List<String> events = new ArrayList<>();
        StreamSink sink = new StreamSink() {
            @Override
            public void append(String delta) {
                visible.append(delta);
            }

            @Override
            public void reset() {
                visible.setLength(0);
                events.add("reset");
            }
        };

        ChatClient client = new RepeatingClient();

        Answer answer = new AnswerService(retriever, client)
                .answer(
                        "What is a constructor?",
                        CodeContext.empty(),
                        new LlmConfig("mock", "mock", "mock", 0.0, 256),
                        5, 0.45, 6000, "en", List.of(),
                        new CancellationToken(),
                        null,
                        sink);

        assertEquals(List.of("reset"), events, "Streamed text must be cleared before the retry");
        assertEquals(CitationStatus.VALIDATED, answer.citationStatus());
        assertEquals(answer.text(), visible.toString());
        assertTrue(answer.text().contains("p.6"));
        assertTrue(answer.warnings().isEmpty());
    }

    /**
     * Cites a page that is not in the context on the first call, then a page that
     * is, which forces the citation repair path.
     */
    private static final class RepeatingClient implements ChatClient {
        private int calls;

        @Override
        public String complete(
                LlmConfig config,
                List<ChatMessage> messages,
                CancellationToken token) {
            return next();
        }

        @Override
        public boolean supportsStreaming() {
            return true;
        }

        @Override
        public String stream(
                LlmConfig config,
                List<ChatMessage> messages,
                CancellationToken token,
                StreamSink sink) {
            String text = next();
            sink.append(text);
            return text;
        }

        private String next() {
            calls++;
            return calls == 1
                    ? "Course basis\n[Lec-2-OOP-with-Java.pdf, p.99] wrong page"
                    : "Course basis\n[Lec-2-OOP-with-Java.pdf, p.6] fixed";
        }
    }
}
