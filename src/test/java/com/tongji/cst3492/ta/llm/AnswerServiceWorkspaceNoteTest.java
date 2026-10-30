package com.tongji.cst3492.ta.llm;

import com.tongji.cst3492.ta.context.CodeFilePolicy;
import com.tongji.cst3492.ta.index.Retriever;
import com.tongji.cst3492.ta.model.CodeContext;
import com.tongji.cst3492.ta.model.CodeFileSnapshot;
import com.tongji.cst3492.ta.model.CourseIndex;
import com.tongji.cst3492.ta.model.WorkspaceContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerServiceWorkspaceNoteTest {
    private static final LlmConfig CONFIG = new LlmConfig("mock", "mock", "mock", 0.0, 256);
    private static final WorkspaceContext FRESH =
            new WorkspaceContext("PROJECT OUTLINE (...):\n--- A.java ---\n", false, 3);
    private static final WorkspaceContext STALE =
            new WorkspaceContext("PROJECT OUTLINE (...):\n--- A.java ---\n", true, 3);
    private static Retriever retriever;

    @BeforeAll
    static void loadIndex() throws IOException {
        try (InputStream input = AnswerServiceWorkspaceNoteTest.class.getResourceAsStream(
                "/index/course-index.json")) {
            assertNotNull(input);
            retriever = new Retriever(CourseIndex.load(input));
        }
    }

    @Test
    void aFreshOutlineAddsNoNote() throws IOException {
        assertTrue(ask(FRESH, "en").contextNote().isEmpty());
    }

    @Test
    void aStaleOutlineExplainsTheInconsistency() throws IOException {
        assertEquals(AnswerService.WORKSPACE_STALE_NOTE, ask(STALE, "en").contextNote());
    }

    @Test
    void staleOutlineAndAttachedFilesAreBothExplained() throws IOException {
        CodeFileSnapshot file = CodeFilePolicy.snapshot(
                "src/main/java/demo/A.java", "JAVA", "class A {}\n");
        Answer answer = ask(STALE, "en", List.of(file));

        assertTrue(answer.contextNote().contains(AnswerService.CONTEXT_NOTE));
        assertTrue(answer.contextNote().contains(AnswerService.WORKSPACE_STALE_NOTE));
    }

    @Test
    void chineseModeExplainsItInChinese() throws IOException {
        assertEquals(AnswerService.WORKSPACE_STALE_NOTE_ZH, ask(STALE, "zh").contextNote());
    }

    private static Answer ask(WorkspaceContext workspace, String language) throws IOException {
        return ask(workspace, language, List.of());
    }

    private static Answer ask(WorkspaceContext workspace, String language,
            List<CodeFileSnapshot> files) throws IOException {
        CodeContext context = new CodeContext("", "", 0, 0, "", "", "", "", "", files, workspace);
        return new AnswerService(retriever, new MockChatClient()).answer(
                "What is an array?", context, CONFIG, 5, 0.45, 6000, language,
                List.of(), new CancellationToken(), null);
    }
}
