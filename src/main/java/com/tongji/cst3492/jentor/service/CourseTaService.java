package com.tongji.cst3492.jentor.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.tongji.cst3492.jentor.index.Retriever;
import com.tongji.cst3492.jentor.llm.Answer;
import com.tongji.cst3492.jentor.llm.AnswerService;
import com.tongji.cst3492.jentor.llm.CancellationToken;
import com.tongji.cst3492.jentor.llm.ChatMessage;
import com.tongji.cst3492.jentor.llm.LlmConfig;
import com.tongji.cst3492.jentor.llm.MockChatClient;
import com.tongji.cst3492.jentor.llm.OpenAiCompatibleClient;
import com.tongji.cst3492.jentor.llm.StreamSink;
import com.tongji.cst3492.jentor.model.CodeContext;
import com.tongji.cst3492.jentor.model.CodeFileSnapshot;
import com.tongji.cst3492.jentor.model.CourseIndex;
import com.tongji.cst3492.jentor.model.DocumentMeta;
import com.tongji.cst3492.jentor.model.WorkspaceContext;
import com.tongji.cst3492.jentor.settings.TaSettings;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@Service(Service.Level.PROJECT)
public final class CourseTaService {
    private final Project project;

    private volatile Retriever retriever;
    private volatile AnswerService realAnswerService;
    private volatile AnswerService mockAnswerService;
    private volatile CodeContext pendingContext;
    private final List<CodeFileSnapshot> pendingFiles = new CopyOnWriteArrayList<>();

    public CourseTaService(Project project) {
        this.project = project;
    }

    public Project getProject() {
        return project;
    }

    public Retriever getRetriever() {
        Retriever local = retriever;
        if (local == null) {
            synchronized (this) {
                local = retriever;
                if (local == null) {
                    local = loadRetriever();
                    retriever = local;
                }
            }
        }
        return local;
    }

    public String getIndexSummary() {
        Retriever local = getRetriever();
        return local.getIndex().getDocuments().size() + " documents, "
                + local.getIndex().getChunks().size() + " chunks, "
                + local.getIndex().getEmbeddingModel();
    }

    public List<DocumentMeta> getDocuments() {
        return getRetriever().getIndex().getDocuments();
    }

    public void loadIndex(Path indexPath) throws IOException {
        try (InputStream input = Files.newInputStream(indexPath)) {
            CourseIndex newIndex = CourseIndex.load(input);
            Retriever newRetriever = new Retriever(newIndex);
            synchronized (this) {
                retriever = newRetriever;
                realAnswerService = null;
                mockAnswerService = null;
            }
        }
    }

    public ChatRequest ask(
            String question,
            CodeContext codeContext,
            List<ChatMessage> history,
            Consumer<String> stageListener) {
        return ask(question, codeContext, history, stageListener, StreamSink.NONE, false);
    }

    public ChatRequest ask(
            String question,
            CodeContext codeContext,
            List<ChatMessage> history,
            Consumer<String> stageListener,
            StreamSink streamSink) {
        return ask(question, codeContext, history, stageListener, streamSink, false);
    }

    /**
     * Asks a question and reports the answer text incrementally through the sink.
     * The sink is ignored when the user disabled streaming in the settings.
     *
     * <p>When {@code includeWorkspace} is set, the workspace outline and a bounded
     * excerpt of the most relevant files are resolved on this background thread and
     * merged into the context, so ticking the option always takes effect even on
     * the first question.</p>
     */
    public ChatRequest ask(
            String question,
            CodeContext codeContext,
            List<ChatMessage> history,
            Consumer<String> stageListener,
            StreamSink streamSink,
            boolean includeWorkspace) {
        TaSettings settings = TaSettings.getInstance();
        TaSettings.State state = settings.getState();
        boolean mock = state.useMockModel;
        String language = "en".equals(state.language) ? "en" : "zh";
        String apiKey = settings.getApiKey();
        if (!mock && (apiKey == null || apiKey.isBlank())) {
            return ChatRequest.failed(new IllegalStateException(
                    "Configure the API key in Settings | Tools | Jentor."));
        }

        LlmConfig config = mock
                ? new LlmConfig("mock", "mock", "mock", 0.0, 256)
                : new LlmConfig(
                        state.baseUrl,
                        apiKey,
                        state.chatModel,
                        state.temperature,
                        state.maxTokens);
        int topK = state.topK;
        double coverageThreshold = state.coverageThreshold;
        int maxContextChars = state.maxContextChars;
        AnswerService answerService = getAnswerService(mock);
        StreamSink sink = state.streamOutput ? streamSink : StreamSink.NONE;
        CancellationToken token = new CancellationToken();
        CompletableFuture<Answer> result = new CompletableFuture<>();
        Future<?> task = AppExecutorUtil.getAppExecutorService().submit(() -> {
            try {
                token.throwIfCancelled();
                CodeContext effectiveContext = includeWorkspace
                        ? codeContext.withWorkspace(workspaceContext(question))
                        : codeContext;
                Answer answer = answerService.answer(
                        question,
                        effectiveContext,
                        config,
                        topK,
                        coverageThreshold,
                        maxContextChars,
                        language,
                        history,
                        token,
                        stageListener,
                        sink);
                token.throwIfCancelled();
                result.complete(answer);
            } catch (CancellationException cancelled) {
                result.cancel(false);
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        return new ChatRequest(result, task, token);
    }

    public void setPendingContext(CodeContext context) {
        pendingContext = context;
    }

    public CodeContext consumePendingContext() {
        CodeContext current = pendingContext;
        pendingContext = null;
        return current;
    }

    /**
     * Resolves the workspace context for a question. Must run off the EDT: it may
     * build the index on first use and reads file text for the excerpts.
     */
    private WorkspaceContext workspaceContext(String question) {
        WorkspaceIndexService indexService = project.getService(WorkspaceIndexService.class);
        return indexService == null ? WorkspaceContext.empty()
                : indexService.contextForBlocking(question);
    }

    /** Queues workspace files for a chat panel that has not been created yet. */
    public void addPendingFiles(List<CodeFileSnapshot> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        pendingFiles.addAll(files);
    }

    public List<CodeFileSnapshot> consumePendingFiles() {
        List<CodeFileSnapshot> current = List.copyOf(pendingFiles);
        pendingFiles.clear();
        return current;
    }

    private AnswerService getAnswerService(boolean mock) {
        AnswerService local = mock ? mockAnswerService : realAnswerService;
        if (local == null) {
            synchronized (this) {
                local = mock ? mockAnswerService : realAnswerService;
                if (local == null) {
                    local = new AnswerService(
                            getRetriever(),
                            mock ? new MockChatClient() : new OpenAiCompatibleClient());
                    if (mock) {
                        mockAnswerService = local;
                    } else {
                        realAnswerService = local;
                    }
                }
            }
        }
        return local;
    }

    private Retriever loadRetriever() {
        try (InputStream input = getClass().getResourceAsStream("/index/course-index.json")) {
            if (input == null) {
                throw new IllegalStateException(
                        "Bundled course index /index/course-index.json is missing.");
            }
            return new Retriever(CourseIndex.load(input));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load the bundled course index", e);
        }
    }

}
