package com.tongji.cst3492.jentor.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.tongji.cst3492.jentor.context.EditGuard;
import com.tongji.cst3492.jentor.context.WorkspaceFileReader;
import com.tongji.cst3492.jentor.llm.ChatClient;
import com.tongji.cst3492.jentor.llm.EditPromptBuilder;
import com.tongji.cst3492.jentor.llm.EditProposalParser;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.tongji.cst3492.jentor.index.Retriever;
import com.tongji.cst3492.jentor.intent.IntentDecisionParser;
import com.tongji.cst3492.jentor.intent.IntentPromptBuilder;
import com.tongji.cst3492.jentor.intent.MockIntentRouter;
import com.tongji.cst3492.jentor.intent.RouteDecision;
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
    private volatile ChatClient realClient;
    private volatile ChatClient mockClient;
    private volatile CourseIndex bundledIndex;
    private volatile CourseIndex importedIndex;
    private volatile long importedAtRevision = -1L;
    private volatile CodeContext pendingContext;
    private final List<CodeFileSnapshot> pendingFiles = new CopyOnWriteArrayList<>();

    public CourseTaService(Project project) {
        this.project = project;
    }

    public Project getProject() {
        return project;
    }

    public Retriever getRetriever() {
        CourseIndex expected = resolveIndex();
        Retriever local = retriever;
        if (local == null || local.getIndex() != expected) {
            synchronized (this) {
                local = retriever;
                if (local == null || local.getIndex() != expected) {
                    local = new Retriever(expected);
                    retriever = local;
                    realAnswerService = null;
                    mockAnswerService = null;
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
                importedIndex = newIndex;
                importedAtRevision = libraryService().getRevision();
                retriever = newRetriever;
                realAnswerService = null;
                mockAnswerService = null;
            }
        }
    }

    public void refreshCourseLibrary() {
        synchronized (this) {
            importedIndex = null;
            importedAtRevision = -1L;
            retriever = null;
            realAnswerService = null;
            mockAnswerService = null;
        }
        libraryService().refreshAll();
    }

    public CourseLibraryService getCourseLibraryService() {
        return libraryService();
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

    private ChatClient chatClient(boolean mock) {
        ChatClient local = mock ? mockClient : realClient;
        if (local == null) {
            synchronized (this) {
                local = mock ? mockClient : realClient;
                if (local == null) {
                    local = mock ? new MockChatClient() : new OpenAiCompatibleClient();
                    if (mock) {
                        mockClient = local;
                    } else {
                        realClient = local;
                    }
                }
            }
        }
        return local;
    }

    /**
     * Classifies the latest message before choosing chat, analysis, edit or status.
     * This request has no side effects; Mock mode uses a deterministic local route.
     */
    public IntentRequest routeIntent(
            String question,
            List<ChatMessage> history,
            String lastAppliedEdit,
            String activeFile,
            String activeSnippet,
            List<String> workspaceFiles,
            String language) {
        TaSettings settings = TaSettings.getInstance();
        TaSettings.State state = settings.getState();
        boolean mock = state.useMockModel;
        String zhLanguage = "zh".equals(language) ? "zh" : "en";
        if (mock) {
            RouteDecision decision = MockIntentRouter.route(
                    question, history, lastAppliedEdit,
                    activeFile != null && !activeFile.isBlank(),
                    "zh".equals(zhLanguage));
            return new IntentRequest(
                    CompletableFuture.completedFuture(decision), null, new CancellationToken());
        }

        String apiKey = settings.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return IntentRequest.failed(new IllegalStateException(
                    "Configure the API key in Settings | Tools | Jentor."));
        }
        LlmConfig config = new LlmConfig(
                state.baseUrl,
                apiKey,
                state.chatModel,
                0.0,
                Math.max(128, Math.min(600, state.maxTokens)));
        List<ChatMessage> messages = new IntentPromptBuilder().build(
                question,
                history,
                lastAppliedEdit,
                activeFile,
                activeSnippet,
                workspaceFiles,
                zhLanguage);
        CompletableFuture<RouteDecision> result = new CompletableFuture<>();
        CancellationToken token = new CancellationToken();
        Future<?> task = AppExecutorUtil.getAppExecutorService().submit(() -> {
            try {
                String reply = chatClient(false).complete(config, messages, token);
                token.throwIfCancelled();
                result.complete(IntentDecisionParser.parse(
                        reply, question, "zh".equals(zhLanguage)));
            } catch (CancellationException cancelled) {
                result.cancel(false);
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        return new IntentRequest(result, task, token);
    }

    /**
     * Runs one edit request: reads the target file, asks for a complete new file
     * and parses the reply. This never writes to disk; applying is a separate,
     * user-approved step.
     */
    public EditRequest edit(
            String instruction,
            String path,
            int startLine,
            int endLine) {
        CompletableFuture<EditProposalParser.Result> result = new CompletableFuture<>();
        TaSettings settings = TaSettings.getInstance();
        TaSettings.State state = settings.getState();
        boolean mock = state.useMockModel;
        String apiKey = settings.getApiKey();
        if (!mock && (apiKey == null || apiKey.isBlank())) {
            return EditRequest.failed(new IllegalStateException(
                    "Configure the API key in Settings | Tools | Jentor."));
        }
        LlmConfig config = mock
                ? new LlmConfig("mock", "mock", "mock", 0.0, 256)
                : new LlmConfig(state.baseUrl, apiKey, state.chatModel, state.temperature,
                        state.maxTokens);
        CancellationToken token = new CancellationToken();
        Future<?> task = AppExecutorUtil.getAppExecutorService().submit(() -> {
            try {
                token.throwIfCancelled();
                if (!EditGuard.isWritablePath(path)) {
                    throw new IllegalStateException(
                            "This file type cannot be edited: " + path);
                }
                VirtualFile file = WorkspaceFileReader.resolve(project, path);
                if (file == null || !file.isValid()) {
                    throw new IllegalStateException("File not found: " + path);
                }
                if (!file.isWritable()) {
                    throw new IllegalStateException("The file is read-only: " + path);
                }
                Document document = ReadAction.computeBlocking(
                        () -> FileDocumentManager.getInstance().getDocument(file));
                if (document == null) {
                    throw new IllegalStateException("Not a text file: " + path);
                }
                long stamp = document.getModificationStamp();
                String text = ReadAction.computeBlocking(() -> WorkspaceFileReader.textOf(file));
                if (text == null) {
                    throw new IllegalStateException("Cannot read the file: " + path);
                }
                if (EditGuard.isTooLarge(text)) {
                    throw new IllegalStateException(
                            "The file is too large to rewrite safely: " + path);
                }
                if (startLine > 0
                        && (endLine < startLine || endLine > document.getLineCount())) {
                    throw new IllegalStateException(
                            "The selected code range is no longer valid; select it again.");
                }
                List<ChatMessage> messages = new EditPromptBuilder().build(
                        instruction, path, file.getFileType().getName(), text, startLine, endLine);
                String reply = chatClient(mock).complete(config, messages, token);
                token.throwIfCancelled();
                EditProposalParser.Result parsed =
                        EditProposalParser.parse(reply, stamp, instruction);
                if (parsed.hasProposal()) {
                    String requestedPath = EditGuard.normalizeRelativePath(path);
                    String proposedPath = EditGuard.normalizeRelativePath(parsed.proposal().path());
                    if (requestedPath.isBlank() || !requestedPath.equals(proposedPath)) {
                        result.complete(new EditProposalParser.Result(
                                null,
                                "the model tried to change a different file: " + proposedPath,
                                false));
                        return;
                    }
                }
                result.complete(parsed);
            } catch (CancellationException cancelled) {
                result.cancel(false);
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        return new EditRequest(result, task, token);
    }

    private AnswerService getAnswerService(boolean mock) {
        AnswerService local = mock ? mockAnswerService : realAnswerService;
        if (local == null) {
            synchronized (this) {
                local = mock ? mockAnswerService : realAnswerService;
                if (local == null) {
                    local = new AnswerService(getRetriever(), chatClient(mock));
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

    private CourseIndex resolveIndex() {
        CourseLibraryService library = libraryService();
        CourseIndex imported = importedIndex;
        if (imported != null && importedAtRevision == library.getRevision()) {
            return imported;
        }
        if (imported != null) {
            synchronized (this) {
                importedIndex = null;
                importedAtRevision = -1L;
            }
        }
        CourseIndex managed = library.getIndex();
        return managed == null ? bundledIndex() : managed;
    }

    private CourseIndex bundledIndex() {
        CourseIndex local = bundledIndex;
        if (local == null) {
            synchronized (this) {
                local = bundledIndex;
                if (local == null) {
                    try (InputStream input = getClass().getResourceAsStream(
                            "/index/course-index.json")) {
                        if (input == null) {
                            throw new IllegalStateException(
                                    "Bundled course index /index/course-index.json is missing.");
                        }
                        local = CourseIndex.load(input);
                        bundledIndex = local;
                    } catch (IOException error) {
                        throw new UncheckedIOException(
                                "Cannot load the bundled course index", error);
                    }
                }
            }
        }
        return local;
    }

    private CourseLibraryService libraryService() {
        CourseLibraryService service = ApplicationManager.getApplication()
                .getService(CourseLibraryService.class);
        if (service == null) {
            throw new IllegalStateException("Jentor course library service is unavailable");
        }
        service.ensureStarted();
        return service;
    }

}
