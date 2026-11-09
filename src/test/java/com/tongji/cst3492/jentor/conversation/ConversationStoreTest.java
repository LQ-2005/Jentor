package com.tongji.cst3492.jentor.conversation;

import com.tongji.cst3492.jentor.llm.Answer;
import com.tongji.cst3492.jentor.llm.CitationStatus;
import com.tongji.cst3492.jentor.model.SourceReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesLoadsAndRestoresAnswerMetadata() throws IOException {
        ConversationStore store = new ConversationStore(temporaryDirectory);
        Answer answer = new Answer(
                "A checked exception must be handled.",
                List.of(new SourceReference(
                        "Lec-1.pdf", "Exceptions", "doc-1", 4, 4,
                        "p.4", "checked exception excerpt", "chunk-1")),
                List.of(),
                true,
                CitationStatus.VALIDATED,
                List.of(),
                "",
                "");
        Conversation conversation = Conversation.create(
                        "conversation-1", "What is a checked exception?", 100L)
                .append(
                        ConversationMessage.user(
                                "What is a checked exception?", 101L, "A.java L1-L3"),
                        ConversationMessage.assistant(answer, 102L),
                        102L);

        store.save(conversation);

        ConversationStore reopened = new ConversationStore(temporaryDirectory);
        Conversation loaded = reopened.load("conversation-1");
        assertEquals(conversation, loaded);
        assertEquals("conversation-1", reopened.lastActiveId());
        assertEquals(1, reopened.summaries().size());
        assertEquals("A.java L1-L3", loaded.messages().get(0).attachmentSummary());
        assertEquals(answer, loaded.messages().get(1).toAnswer());
        assertFalse(loaded.messages().get(1).editResult());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("conversation-1.json")));

        Conversation renamed = reopened.rename("conversation-1", "Renamed conversation");
        assertEquals("Renamed conversation", renamed.title());
        assertEquals("Renamed conversation",
                new ConversationStore(temporaryDirectory).load("conversation-1").title());
    }

    @Test
    void deletesConversationAndClearsActiveId() throws IOException {
        ConversationStore store = new ConversationStore(temporaryDirectory);
        store.save(Conversation.create("conversation-1", "Temporary", 10L));

        store.delete("conversation-1");

        assertTrue(store.summaries().isEmpty());
        assertTrue(store.lastActiveId().isBlank());
        assertFalse(Files.exists(temporaryDirectory.resolve("conversation-1.json")));
    }

    @Test
    void rebuildsACorruptIndexFromConversationFiles() throws IOException {
        ConversationStore store = new ConversationStore(temporaryDirectory);
        store.save(Conversation.create("conversation-1", "Keep this", 10L));
        Files.writeString(temporaryDirectory.resolve("index.json"), "{not-json");

        ConversationStore reopened = new ConversationStore(temporaryDirectory);

        assertEquals(1, reopened.summaries().size());
        assertEquals("Keep this", reopened.summaries().get(0).title());
    }

    @Test
    void rejectsUnsafeConversationIds() {
        ConversationStore store = new ConversationStore(temporaryDirectory);
        assertThrows(IOException.class, () -> store.load("../outside"));
        assertThrows(IOException.class, () -> store.delete("..\\outside"));
    }

    @Test
    void createsACompactTitleFromTheFirstQuestion() {
        Conversation conversation = Conversation.create(
                "conversation-1",
                "  This   is a deliberately long question that should become a short title "
                        + "without leaking a huge amount of text into the history list.  ",
                10L);

        assertNotNull(conversation.title());
        assertTrue(conversation.title().endsWith("…"));
        assertTrue(conversation.title().length() <= 48);
    }

    @Test
    void editOnlyConversationIsDiscoverableAndRestoresTheOriginalRequest() throws IOException {
        String question = "只修改 quickSort 方法里的重复判断";
        ConversationTurn turn = new ConversationTurn(question, "Main.java L12-L20", 100L);
        Conversation conversation = turn.appendTo(null,
                ConversationMessage.editResult("已修改：Main.java", 200L));
        new ConversationStore(temporaryDirectory).save(conversation);

        ConversationStore reopened = new ConversationStore(temporaryDirectory);
        assertEquals(1, reopened.summaries().size());
        Conversation loaded = reopened.loadActive();
        assertNotNull(loaded);
        assertEquals(question, loaded.title());
        assertEquals(100L, loaded.createdAt());
        assertEquals(200L, loaded.updatedAt());
        assertEquals(List.of("user", "assistant"),
                loaded.messages().stream().map(ConversationMessage::role).toList());
        assertEquals(question, loaded.messages().getFirst().content());
        assertEquals("Main.java L12-L20", loaded.messages().getFirst().attachmentSummary());
        assertEquals(100L, loaded.messages().getFirst().timestamp());
        ConversationMessage result = loaded.messages().getLast();
        assertTrue(result.editResult());
        assertEquals("已修改：Main.java", result.content());
        assertEquals(CitationStatus.NOT_APPLICABLE, result.citationStatus());
        assertTrue(result.sources().isEmpty());
        assertTrue(result.disclosure().isBlank());
        assertEquals(result.content(), reopened.summaries().getFirst().preview());
    }

    @Test
    void appendsAnEditToExistingChatWithoutDuplicatingTheAssistant() throws IOException {
        Conversation chat = new ConversationTurn("解释这段代码", "Main.java", 10L)
                .appendTo(null, ConversationMessage.assistant("这里有重复判断。", 20L));
        Conversation updated = new ConversationTurn("按这个改", "Main.java L12-L20", 30L)
                .appendTo(chat, ConversationMessage.editResult("已修改：Main.java", 40L));
        new ConversationStore(temporaryDirectory).save(updated);

        Conversation loaded = new ConversationStore(temporaryDirectory).loadActive();
        assertNotNull(loaded);
        assertEquals(chat.id(), loaded.id());
        assertEquals(chat.title(), loaded.title());
        assertEquals(10L, loaded.createdAt());
        assertEquals(40L, loaded.updatedAt());
        assertEquals(List.of("解释这段代码", "这里有重复判断。", "按这个改", "已修改：Main.java"),
                loaded.messages().stream().map(ConversationMessage::content).toList());
        assertFalse(loaded.messages().get(1).editResult());
        assertTrue(loaded.messages().get(3).editResult());
    }

    @Test
    void cancelledAndUnchangedEditsRemainVisibleAsCompletedTurns() throws IOException {
        ConversationStore store = new ConversationStore(temporaryDirectory);
        Conversation conversation = new ConversationTurn("修改所选方法", "A.java L1-L3", 10L)
                .appendTo(null, ConversationMessage.editResult("已取消修改。", 20L));
        store.save(conversation);
        conversation = new ConversationTurn("再检查一下", "A.java L1-L3", 30L)
                .appendTo(store.loadActive(), ConversationMessage.editResult("无需修改。", 40L));
        store.save(conversation);

        Conversation loaded = new ConversationStore(temporaryDirectory).loadActive();
        assertNotNull(loaded);
        assertEquals(4, loaded.messages().size());
        assertEquals(List.of("已取消修改。", "无需修改。"), loaded.messages().stream()
                .filter(ConversationMessage::editResult)
                .map(ConversationMessage::content).toList());
    }

    @Test
    void loadsLegacyMessagesWithoutAnEditResultField() throws IOException {
        Files.writeString(temporaryDirectory.resolve("legacy.json"), """
                {
                  "version": 1,
                  "id": "legacy",
                  "title": "Existing chat",
                  "createdAt": 10,
                  "updatedAt": 20,
                  "messages": [
                    {
                      "role": "user", "content": "Question", "timestamp": 10,
                      "attachmentSummary": "Main.java", "grounded": false,
                      "citationStatus": "NOT_APPLICABLE", "sources": [],
                      "warnings": [], "disclosure": "", "contextNote": ""
                    },
                    {
                      "role": "assistant", "content": "Answer", "timestamp": 20,
                      "attachmentSummary": "", "grounded": false,
                      "citationStatus": "NOT_APPLICABLE", "sources": [],
                      "warnings": [], "disclosure": "", "contextNote": ""
                    }
                  ]
                }
                """);

        ConversationStore store = new ConversationStore(temporaryDirectory);
        Conversation loaded = store.load("legacy");

        assertEquals(List.of("Question", "Answer"),
                loaded.messages().stream().map(ConversationMessage::content).toList());
        assertTrue(loaded.messages().stream().noneMatch(ConversationMessage::editResult));
        store.save(loaded);
        assertEquals(loaded, new ConversationStore(temporaryDirectory).loadActive());
    }
}
