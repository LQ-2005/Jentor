package com.tongji.cst3492.ta.llm;

import com.tongji.cst3492.ta.context.CodeFilePolicy;
import com.tongji.cst3492.ta.index.Retriever;
import com.tongji.cst3492.ta.model.CodeContext;
import com.tongji.cst3492.ta.model.CodeFileSnapshot;
import com.tongji.cst3492.ta.model.CourseIndex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerContextNoteTest {
    private static final LlmConfig CONFIG = new LlmConfig("mock", "mock", "mock", 0.0, 256);
    private static Retriever retriever;

    @BeforeAll
    static void loadIndex() throws IOException {
        try (InputStream input = AnswerContextNoteTest.class.getResourceAsStream(
                "/index/course-index.json")) {
            assertNotNull(input, "Bundled index must be present");
            retriever = new Retriever(CourseIndex.load(input));
        }
    }

    @Test
    void answersWithoutFilesCarryNoNote() throws IOException {
        assertTrue(ask(CodeContext.empty(), "en").contextNote().isEmpty());
    }

    @Test
    void attachedFilesExplainThatTheSnapshotMayDrift() throws IOException {
        Answer answer = ask(withFile(), "en");

        assertEquals(AnswerService.CONTEXT_NOTE, answer.contextNote());
        assertTrue(answer.contextNote().contains("snapshot"));
    }

    @Test
    void chineseModeExplainsTheDriftInChinese() throws IOException {
        assertEquals(AnswerService.CONTEXT_NOTE_ZH, ask(withFile(), "zh").contextNote());
    }

    private static CodeContext withFile() {
        CodeFileSnapshot file = CodeFilePolicy.snapshot(
                "src/main/java/demo/ArrayDemo.java", "JAVA",
                "int[] values = new int[3];\nvalues[0] = 1;\n");
        return CodeContext.ofFiles(List.of(file));
    }

    private static Answer ask(CodeContext context, String language) throws IOException {
        return new AnswerService(retriever, new MockChatClient()).answer(
                "What is an array?", context, CONFIG, 5, 0.45, 6000, language,
                List.of(), new CancellationToken(), null);
    }
}
