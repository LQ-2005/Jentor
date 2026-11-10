package com.tongji.cst3492.jentor.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.tongji.cst3492.jentor.util.AtomicFileWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Project-local conversation persistence. Files live under the IDE metadata
 * directory, which this repository ignores, so private chats cannot be committed
 * accidentally.
 */
public final class ConversationStore {
    private static final Logger LOG = Logger.getLogger(ConversationStore.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String INDEX_FILE = "index.json";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private final Path directory;
    private ConversationIndex index;

    public ConversationStore(Path directory) {
        this.directory = directory;
    }

    public static ConversationStore forProject(Project project) {
        String basePath = project.getBasePath();
        Path directory = basePath == null
                ? PathManager.getSystemDir().resolve("jentor-conversations")
                : Path.of(basePath).resolve(".idea").resolve("jentor").resolve("conversations");
        return new ConversationStore(directory);
    }

    public Path directory() {
        return directory;
    }

    public synchronized List<ConversationSummary> summaries() {
        return index().conversations().stream()
                .sorted(Comparator.comparingLong(ConversationSummary::updatedAt).reversed())
                .toList();
    }

    public synchronized String lastActiveId() {
        return index().lastActiveId();
    }

    public synchronized Conversation load(String id) throws IOException {
        requireSafeId(id);
        Path file = conversationFile(id);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Conversation not found: " + id);
        }
        Conversation conversation = MAPPER.readValue(file.toFile(), Conversation.class);
        if (conversation == null || !id.equals(conversation.id())) {
            throw new IOException("Invalid conversation file: " + file);
        }
        return conversation;
    }

    public synchronized Conversation loadActive() throws IOException {
        String activeId = index().lastActiveId();
        return activeId.isBlank() ? null : load(activeId);
    }

    public synchronized void save(Conversation conversation) throws IOException {
        requireSafeId(conversation.id());
        Files.createDirectories(directory);
        AtomicFileWriter.write(conversationFile(conversation.id()),
                MAPPER.writeValueAsBytes(conversation));

        List<ConversationSummary> updated = new ArrayList<>();
        for (ConversationSummary summary : index().conversations()) {
            if (!conversation.id().equals(summary.id())) {
                updated.add(summary);
            }
        }
        updated.add(conversation.summary());
        writeIndex(new ConversationIndex(1, conversation.id(), updated));
    }

    public synchronized void delete(String id) throws IOException {
        requireSafeId(id);
        Files.deleteIfExists(conversationFile(id));
        List<ConversationSummary> updated = index().conversations().stream()
                .filter(summary -> !id.equals(summary.id()))
                .toList();
        String activeId = id.equals(index().lastActiveId()) ? "" : index().lastActiveId();
        writeIndex(new ConversationIndex(1, activeId, updated));
    }

    public synchronized Conversation rename(String id, String title) throws IOException {
        Conversation renamed = load(id).withTitle(title);
        save(renamed);
        return renamed;
    }

    public synchronized void setLastActive(String id) throws IOException {
        if (id != null && !id.isBlank()) {
            requireSafeId(id);
        }
        writeIndex(new ConversationIndex(
                1, id == null ? "" : id, index().conversations()));
    }

    private ConversationIndex index() {
        if (index != null) {
            return index;
        }
        Path indexPath = directory.resolve(INDEX_FILE);
        if (Files.isRegularFile(indexPath)) {
            try {
                ConversationIndex loaded = MAPPER.readValue(indexPath.toFile(), ConversationIndex.class);
                if (loaded != null) {
                    List<ConversationSummary> safe = loaded.conversations().stream()
                            .filter(summary -> SAFE_ID.matcher(summary.id()).matches())
                            .toList();
                    String activeId = SAFE_ID.matcher(loaded.lastActiveId()).matches()
                            ? loaded.lastActiveId() : "";
                    index = new ConversationIndex(1, activeId, safe);
                    return index;
                }
            } catch (IOException | RuntimeException error) {
                LOG.log(java.util.logging.Level.WARNING,
                        "Cannot read Jentor conversation index; rebuilding it", error);
            }
        }
        index = rebuildIndex();
        return index;
    }

    private ConversationIndex rebuildIndex() {
        if (!Files.isDirectory(directory)) {
            return ConversationIndex.empty();
        }
        List<ConversationSummary> summaries = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !INDEX_FILE.equals(path.getFileName().toString()))
                    .forEach(path -> {
                        try {
                            Conversation conversation =
                                    MAPPER.readValue(path.toFile(), Conversation.class);
                            if (conversation != null && SAFE_ID.matcher(conversation.id()).matches()) {
                                summaries.add(conversation.summary());
                            }
                        } catch (IOException | RuntimeException error) {
                            LOG.log(java.util.logging.Level.WARNING,
                                    "Skipping unreadable Jentor conversation: " + path, error);
                        }
                    });
        } catch (IOException error) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Cannot scan Jentor conversation directory", error);
        }
        summaries.sort(Comparator.comparingLong(ConversationSummary::updatedAt).reversed());
        String active = summaries.isEmpty() ? "" : summaries.get(0).id();
        ConversationIndex rebuilt = new ConversationIndex(1, active, summaries);
        try {
            Files.createDirectories(directory);
            AtomicFileWriter.write(directory.resolve(INDEX_FILE), MAPPER.writeValueAsBytes(rebuilt));
        } catch (IOException error) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Cannot persist rebuilt Jentor conversation index", error);
        }
        return rebuilt;
    }

    private void writeIndex(ConversationIndex updated) throws IOException {
        Files.createDirectories(directory);
        AtomicFileWriter.write(directory.resolve(INDEX_FILE), MAPPER.writeValueAsBytes(updated));
        index = updated;
    }

    private Path conversationFile(String id) {
        return directory.resolve(id + ".json");
    }

    private static void requireSafeId(String id) throws IOException {
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            throw new IOException("Invalid conversation id");
        }
    }

}
