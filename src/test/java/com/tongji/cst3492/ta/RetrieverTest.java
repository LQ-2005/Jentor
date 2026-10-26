package com.tongji.cst3492.ta;

import com.tongji.cst3492.ta.index.Retriever;
import com.tongji.cst3492.ta.index.Tokens;
import com.tongji.cst3492.ta.index.CourseGlossary;
import com.tongji.cst3492.ta.llm.Answer;
import com.tongji.cst3492.ta.llm.AnswerService;
import com.tongji.cst3492.ta.llm.ChatMessage;
import com.tongji.cst3492.ta.llm.CancellationToken;
import com.tongji.cst3492.ta.llm.CitationStatus;
import com.tongji.cst3492.ta.llm.CitationValidator;
import com.tongji.cst3492.ta.llm.LlmConfig;
import com.tongji.cst3492.ta.llm.MockChatClient;
import com.tongji.cst3492.ta.llm.PromptBuilder;
import com.tongji.cst3492.ta.model.CodeContext;
import com.tongji.cst3492.ta.model.Chunk;
import com.tongji.cst3492.ta.model.ContextBlock;
import com.tongji.cst3492.ta.model.CourseIndex;
import com.tongji.cst3492.ta.model.RetrievedChunk;
import com.tongji.cst3492.ta.model.SourceReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrieverTest {
    private static CourseIndex index;
    private static Retriever retriever;

    @BeforeAll
    static void loadIndex() throws IOException {
        try (InputStream input = RetrieverTest.class.getResourceAsStream(
                "/index/course-index.json")) {
            assertNotNull(input, "Bundled index must be present");
            index = CourseIndex.load(input);
        }
        retriever = new Retriever(index);
    }

    @Test
    void indexContainsTheWholeCourse() {
        assertEquals(9, index.getDocuments().size());
        assertEquals(214, index.getChunks().size());
        assertEquals(512, index.getDimensions());
    }

    @Test
    void tokenizerMatchesTheIndexer() {
        assertEquals(
                List.of("difference", "abstract", "class", "interface"),
                Tokens.tokenize(
                        "What is the difference between an abstract class and an interface?"));
        assertEquals(List.of("generic", "type"), Tokens.tokenize("What is a generic type?"));
    }

    @Test
    void knownQuestionsHitTheExpectedPage() {
        assertPage("What is a checked exception?", "Lec-3.1-Exceptions.pdf", 5);
        assertPage("What is a generic type?", "Lec-3.2-Generics-and-Collections.pdf", 5);
        assertPage("What is a constructor?", "Lec-2-OOP-with-Java.pdf", 6);
        assertPage("What is BM25?", "Lec-5-Building-AI-Powered-Java-Applications.pdf", 24);
    }

    @Test
    void outOfScopeQuestionsAreRejected() {
        assertFalse(retriever.isGrounded(
                retriever.retrieve("What is the weather in Shanghai today?", 5)));
        assertFalse(retriever.isGrounded(
                retriever.retrieve("How do I bake a chocolate cake?", 5)));
    }

    @Test
    void mockAnswerUsesAValidCitation() throws IOException {
        LlmConfig config = new LlmConfig("mock", "mock", "mock", 0.0, 256);
        Answer answer = new AnswerService(retriever, new MockChatClient())
                .answer("What is an array?", CodeContext.empty(),
                        config, 5, 0.45, 6000, "en", List.of(),
                        new CancellationToken(), null);
        assertTrue(answer.grounded());
        assertEquals(CitationStatus.VALIDATED, answer.citationStatus());
        assertFalse(new CitationValidator().parse(answer.text()).isEmpty());
        assertFalse(answer.sources().isEmpty());
        assertTrue(answer.warnings().isEmpty());
    }

    @Test
    void generalKnowledgePathUsesTheRequiredSentence() throws IOException {
        LlmConfig config = new LlmConfig("mock", "mock", "mock", 0.0, 256);
        Answer answer = new AnswerService(retriever, new MockChatClient())
                .answer(
                        "What is the weather in Shanghai today?",
                        CodeContext.empty(), config, 5, 0.45, 6000, "en", List.of(),
                        new CancellationToken(), null);
        assertFalse(answer.grounded());
        assertEquals(CitationStatus.NOT_APPLICABLE, answer.citationStatus());
        assertTrue(answer.sources().isEmpty());
        assertEquals(PromptBuilder.FALLBACK_SENTENCE, answer.disclosure());
        assertFalse(answer.text().startsWith(PromptBuilder.FALLBACK_SENTENCE));
    }

    @Test
    void citationValidationUsesActualContextAndDedupesSources() {
        Chunk chunk = new Chunk(
                "c1",
                "d1",
                "Lec-3.1-Exceptions",
                "Lec-3.1-Exceptions.pdf",
                "PAGE",
                5,
                5,
                0,
                "Three Categories of Exceptions",
                "Checked exception: exceptional conditions that a well-written application "
                        + "should anticipate and recover from.",
                new float[512]);
        ContextBlock block = new ContextBlock(chunk, chunk.getText(), false);
        CitationValidator validator = new CitationValidator();

        assertTrue(validator.invalidCitations(
                "Course basis\n[Lec-3.1-Exceptions.pdf, p.5]", List.of(block)).isEmpty());
        assertEquals(1, validator.invalidCitations(
                "[Lec-3.1-Exceptions.pdf, p.9]", List.of(block)).size());
        assertEquals(1, validator.invalidCitations(
                "[Other.pdf, p.5]", List.of(block)).size());
        assertTrue(validator.invalidCitations(
                "No citation here", List.of(block)).contains("NO_CITATIONS"));

        List<SourceReference> sources = validator.citedSources(
                "Course basis\n[Lec-3.1-Exceptions.pdf, p.5] "
                        + "[Lec-3.1-Exceptions.pdf, p.5]", List.of(block));
        assertEquals(1, sources.size());
        assertEquals("p.5", sources.get(0).locator());
        assertTrue(validator.invalidCitations(
                "课程依据\n[Lec-3.1-Exceptions.pdf, p.5]\n"
                        + "通用补充\n[Lec-3.1-Exceptions.pdf, p.5]",
                List.of(block)).contains("CITATION_IN_GENERAL_SECTION"));
    }

    @Test
    void chineseTerminologyExpandsToEnglishCourseMaterial() {
        String expanded = CourseGlossary.expand("什么是受检异常？");
        assertTrue(expanded.contains("checked exception"));
        List<RetrievedChunk> results = retriever.retrieve(expanded, 3);
        assertTrue(results.stream().anyMatch(result ->
                result.getChunk().getFileName().equals("Lec-3.1-Exceptions.pdf")
                        && result.getChunk().getPageStart() <= 5
                        && 5 <= result.getChunk().getPageEnd()));
    }

    @Test
    void promptIncludesHistoryAndCodeContext() {
        CodeContext context = new CodeContext(
                "Foo.java",
                "JAVA",
                10,
                12,
                "public void foo()",
                "class Foo",
                "int before = 1;",
                "foo();",
                "int after = 2;");
        List<ChatMessage> history = List.of(
                ChatMessage.user("previous question"),
                new ChatMessage("assistant", "previous answer"));

        List<ChatMessage> messages = new PromptBuilder()
                .buildGeneralMessages("follow-up", context, "en", history);

        String prompt = messages.get(messages.size() - 1).content();
        assertTrue(prompt.contains("--- selected code ---"));
        assertTrue(prompt.contains("foo();"));
        assertTrue(messages.stream().anyMatch(message ->
                "previous question".equals(message.content())));
    }

    private void assertPage(String question, String fileName, int page) {
        List<RetrievedChunk> results = retriever.retrieve(question, 3);
        assertTrue(
                results.stream().anyMatch(result ->
                        result.getChunk().getFileName().equals(fileName)
                                && result.getChunk().getPageStart() <= page
                                && page <= result.getChunk().getPageEnd()),
                () -> question + " should retrieve " + fileName + " p." + page);
    }
}
