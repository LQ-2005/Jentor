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
}
