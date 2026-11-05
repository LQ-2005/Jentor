package com.tongji.cst3492.jentor.llm;

import com.tongji.cst3492.jentor.context.CodeFilePolicy;
import com.tongji.cst3492.jentor.index.Retriever;
import com.tongji.cst3492.jentor.model.CodeContext;
import com.tongji.cst3492.jentor.model.CodeFileSnapshot;
import com.tongji.cst3492.jentor.model.CourseIndex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the last mile of the feature: an attached file has to end up inside the
 * prompt that is actually sent to the model, and only there.
 */
class WorkspaceFileInjectionTest {
    private static final LlmConfig CONFIG = new LlmConfig("mock", "mock", "mock", 0.0, 256);
    private static final String MARKER = "private final OrderRepository repository;";
    private static Retriever retriever;

    @BeforeAll
    static void loadIndex() throws IOException {
        try (InputStream input = WorkspaceFileInjectionTest.class.getResourceAsStream(
                "/index/course-index.json")) {
            assertNotNull(input, "Bundled index must be present");
            retriever = new Retriever(CourseIndex.load(input));
        }
    }

    @Test
    void attachedFileContentReachesTheModelPrompt() throws IOException {
        RecordingClient client = new RecordingClient();

        Answer answer = new AnswerService(retriever, client).answer(
                "这个类里的 placeOrder 做了什么？",
                CodeContext.ofFiles(List.of(orderService())),
                CONFIG, 5, 0.45, 6000, "en", List.of(),
                new CancellationToken(), null);

        assertFalse(client.calls.isEmpty(), "the model must be called");
        List<ChatMessage> messages = client.calls.get(0);
        String system = messages.get(0).content();
        String user = messages.get(messages.size() - 1).content();

        assertTrue(user.contains("WORKSPACE FILES"), "workspace section missing:\n" + user);
        assertTrue(user.contains("src/main/java/demo/OrderService.java"), "file path missing");
        assertTrue(user.contains(MARKER), "file body missing");
        assertFalse(system.contains(MARKER), "the file body must stay in the user prompt");
        assertTrue(answer.contextNote().startsWith(AnswerService.CONTEXT_NOTE));
        assertTrue(answer.contextNote().contains("OrderService.java"));
    }

    @Test
    void selectionAndFilesAreBothInjectedWithTheSelectionFirst() throws IOException {
        RecordingClient client = new RecordingClient();
        CodeContext context = new CodeContext("Demo.java", "JAVA", 10, 12, "void run()",
                "class Demo", "int before;", "run();", "int after;")
                .withFiles(List.of(orderService()));

        new AnswerService(retriever, client).answer(
                "这段代码和这个类有什么关系？", context,
                CONFIG, 5, 0.45, 6000, "en", List.of(),
                new CancellationToken(), null);

        String user = client.calls.get(0).get(client.calls.get(0).size() - 1).content();
        int selection = user.indexOf("--- selected code ---");
        int files = user.indexOf("WORKSPACE FILES");
        assertTrue(selection >= 0, "selection section missing");
        assertTrue(files > selection, "files must follow the selection");
        assertTrue(user.contains(MARKER), "file body missing");
    }

    @Test
    void noFilesKeepsThePromptFreeOfAWorkspaceSection() throws IOException {
        RecordingClient client = new RecordingClient();

        new AnswerService(retriever, client).answer(
                "What is an array?", CodeContext.empty(),
                CONFIG, 5, 0.45, 6000, "en", List.of(),
                new CancellationToken(), null);

        String user = client.calls.get(0).get(client.calls.get(0).size() - 1).content();
        assertFalse(user.contains("WORKSPACE FILES"));
    }

    private static CodeFileSnapshot orderService() {
        return CodeFilePolicy.snapshot(
                "src/main/java/demo/OrderService.java", "JAVA",
                "class OrderService {\n    " + MARKER + "\n"
                        + "    Order placeOrder() { return null; }\n}\n");
    }

    /** Records every prompt that reaches the client and answers like the mock. */
    private static final class RecordingClient implements ChatClient {
        private final List<List<ChatMessage>> calls = new ArrayList<>();
        private final MockChatClient delegate = new MockChatClient();

        @Override
        public String complete(
                LlmConfig config,
                List<ChatMessage> messages,
                CancellationToken token) {
            calls.add(List.copyOf(messages));
            return delegate.complete(config, messages, token);
        }
    }
}
