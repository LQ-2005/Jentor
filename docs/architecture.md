# Architecture

## Goal

Jentor is a course-specific teaching assistant inside IntelliJ IDEA. It retrieves
CST3492 lecture material, answers with document and page citations, and explicitly
falls back to general Java knowledge when the slides do not cover the question.

## Pipeline

```text
PDF slides
  -> page extraction (default or layout mode)
  -> page-bounded chunks
  -> hashed TF-IDF vectors + IDF table
  -> course-index.json

Question + selected code
  -> tokenizer
  -> BM25 + vector tie-breaker + title/phrase bonus
  -> top-k chunks + coverage gate
  -> grounded prompt
  -> OpenAI-compatible chat endpoint (server-sent events)
  -> streamed preview in the tool window
  -> citation validation and one retry
  -> final answer replaces the preview
```

## Indexing

There are two compatible sources for the page-aware index:

1. The primary managed course library, built inside the plugin with PDFBox.
2. The optional Python script `tools/build_index.py`, retained for reproducible
   offline index generation and bundled fallback material.

`CourseLibraryService` owns a catalog of independent course-file entries. Each entry may
live in a different folder and stores its original path, content hash, size,
modification time, page/chunk/table/image counts, OCR/parser metadata and indexing status. Exact source paths are
watched through VFS, and a 30-second size/mtime sweep covers missed events.
`CourseDocumentParser` routes PDF, DOCX/PPTX/XLSX, legacy Office, plain text,
Markdown, HTML, CSV and images to dedicated parsers. PDFBox extracts sorted/layout
and default PDF text, OCRs embedded images and low-text pages, and marks
table-like multiline layouts. Apache POI preserves Office table row/cell models;
JSoup handles HTML structure; Tess4J performs offline `chi_sim+eng` OCR.
`CourseLibraryStore` persists
`catalog.json` and the aggregate `course-index.json` atomically.

The previous index remains active until parsing, vector generation and JSON
serialization succeed. A failed or moved PDF is marked on its own row and does not
break unrelated materials. Adding or replacing one PDF reuses the parsed chunks of
unchanged documents and regenerates the global IDF/vector aggregate.

- The Python path extracts page text with pypdf; the managed path uses PDFBox.
- Two extraction modes are compared. The layout mode preserves code indentation
  but animated slides can produce text such as `Pa s s i n g`. If the layout
  result has a high ratio of single-letter tokens, the default extraction is used
  instead.
- Footer page numbers are removed when they can be identified safely.
- Chunks never cross a page boundary. A page up to 900 characters becomes one
  chunk; longer pages are split at line boundaries with a 120-character overlap.
- Every chunk stores `docId`, `docTitle`, `fileName`, `pageStart`, `pageEnd`,
  `locatorType`, `chunkIndex`, `title`, `text`, and a 512-dimensional vector.
  Locator types include `PAGE`, `SLIDE`, `SECTION`, `SHEET`, and `IMAGE`.

The bundled Python index currently contains 9 documents, 199 pages, and 214
chunks. PDFBox extraction is not byte-for-byte identical to pypdf; the same
lecture set currently produces 203 chunks in the Java builder. Document names,
PDF page numbers and the citation contract are unchanged.

## Local embedding

The bundled index uses a deterministic hashed TF-IDF embedding:

1. Normalize text with NFKC and lowercase Latin tokens.
2. Extract identifiers with `[A-Za-z0-9_]+` and CJK characters.
3. Split camelCase, snake_case, and letter/digit boundaries.
4. Remove English stopwords while preserving Java keywords.
5. Apply light plural stemming.
6. Hash each token with SHA-256 into 512 signed dimensions.
7. Weight by `(1 + log(tf)) * idf` and L2-normalize.

The Python indexer and Java runtime use the same tokenizer rules. A unit test
checks representative token sequences.

## Retrieval

Retrieval is BM25-first because the corpus is small and student questions usually
use terminology that appears verbatim in the slides.

- BM25: `k1 = 1.2`, `b = 0.75`.
- Vector score: cosine similarity on the hashed TF-IDF vector.
- Fusion: `0.85 * z(BM25) + 0.15 * z(cosine)`.
- Title/phrase bonus: an exact title match, a title subset match, and matching
  query bigrams receive additional weight. This prevents a long overview slide
  from outranking the slide that actually defines the requested term.
- Top-k: 5 by default.
- Coverage gate: the top chunk must contain at least 45% of the question's
  IDF-weighted content terms. If it does not, the answer takes the general
  knowledge path.

The corpus is small enough for brute-force cosine similarity: 214 vectors of 512
dimensions. There is no external vector database, which keeps installation and
demo risk low.

## Generation and citations

The system prompt requires:

- answers grounded in the supplied excerpts when they are relevant;
- citations in the form `[document, <locator>]`, including PDF pages, slides,
  sections, sheets and images;
- no invented document names or pages;
- the exact sentence
  `Response is based on general knowledge; no specific course material is referenced.`
  when the excerpts are insufficient.

The UI and answer language can be switched between Chinese and English. In
Chinese mode the equivalent fallback sentence
`本回答基于通用知识，未引用具体课程材料。` is used; English mode keeps the original
English sentence. The assignment gives the English wording as an example (`e.g.`),
so the localized equivalent preserves the required explicit disclosure.
`AnswerService` strips the disclosure from the answer body and returns it as a
separate `disclosure` field; the UI renders it as a small gray footnote.

`PromptBuilder.buildRagContext` returns both the exact text sent to the model and
the `ContextBlock` objects used. `CitationValidator` checks citations against
those blocks, not the raw Top-K list, so a citation to a trimmed-away chunk is
invalid. Invalid or missing citations trigger one stricter retry. The final
state is one of `VALIDATED`, `FAILED`, or `NOT_APPLICABLE`; the UI never presents
mechanical document/page validation as semantic correctness.

Only citations that appear in the answer and pass validation become source
entries; they are deduplicated by document and page range. Unused retrieval
candidates stay internal and are not exposed in the answer card. Clicking a
source opens the document name, PDF page, and the actual excerpt sent in this
request. The course index uses PDF page numbers, so citations use `p.`/`pp.`
rather than mixing `Slide`.

## Streaming

`ChatClient.stream` delivers the answer incrementally through a `StreamSink`.
`OpenAiCompatibleClient` sends `stream: true` and reads the server-sent events:

- Each `data:` line is parsed as a chunk; only `choices[0].delta.content` (or a
  `message.content` fallback) is forwarded, so role-only and `finish_reason`
  chunks produce no visible text.
- Comment lines such as `: keep-alive` and blank event separators are ignored,
  and `data: [DONE]` ends the stream.
- Providers that ignore `stream` and answer with a normal JSON body are handled
  by reading the whole body once and emitting it as a single delta.
- Cancelling the token closes the response body, which interrupts the blocking
  read and surfaces as `CancellationException`.

`AnswerService` treats the deltas as a preview, never as the answer:

- The general-knowledge path may prepend the fallback sentence and
  `stripDisclosure` removes it from the body, so `Answer.text()` can differ from
  the concatenated deltas.
- A citation retry calls `StreamSink.reset()` first, so the rejected draft
  disappears instead of remaining on screen.
- `ChatPanel` therefore always renders `Answer.text()` when the request
  completes, and drops the preview card when the request fails.

The UI never repaints per token. Deltas are appended to a per-request buffer from
the worker thread, and an 80 ms Swing timer drains that buffer and re-renders the
message body, which keeps the EDT responsive at high token rates. While the answer
is streaming, fenced code blocks are painted as plain read-only text areas; the
real editor components are only created for the final render, so a fast stream
cannot create and discard an editor several times per second. The per-request
buffer is replaced on every new request, so a stale response cannot write into the
current conversation, and switching/new-chat/stop drop the buffer immediately. The same
streaming path is used by `MockChatClient`, which splits its deterministic answer
into small chunks so the behaviour is testable offline.

Streaming can be switched off in the settings; `CourseTaService` then passes
`StreamSink.NONE`, `AnswerService` falls back to `ChatClient.complete`, and the
answer appears in one piece as before.

## Workspace files

`AttachFileAction` reads workspace source files into `CodeFileSnapshot` records,
and `ChatPanel` keeps them next to the selected code. The rules live in
`context/CodeFilePolicy`, which has no IntelliJ types so it can be unit tested:

- A source-only whitelist (`.java`, `.kt`, `.groovy`, `.gradle`, `.kts`).
  Config/data formats such as `.properties`, `.xml` and `.json`, compiled
  `.class` files and every binary type are rejected.
- Names that usually hold credentials (`.env`, `secret*`, `credential*`,
  `*.pem`, `*.key`, `*.p12`, `*.jks`, `id_rsa*`) are refused, and admitted text
  is scanned for obvious private keys or API-token assignments.
- Up to five files, 400 lines or 4000 characters per file, a 10000-character
  budget for the file section, and a 16000-character global IDE-context budget.
  Truncation keeps whole lines and records the range that survived.

`WorkspaceFileReader` is the only part that touches the platform.
`AttachFileAction` runs it inside a `ReadAction` on a pooled thread, so the EDT
never touches the disk, and it prefers `FileDocumentManager.getDocument` so
unsaved editor changes are captured. Files outside the project content, excluded
directories and library roots are skipped.

A snapshot is taken once, when the file is attached, and never refreshed. That
keeps the context of a running conversation stable while the user keeps editing;
the price is that an answer may describe an older version of the file.
`AnswerService` therefore sets `Answer.contextNote`, rendered as a footnote,
whenever files were attached.

`PromptBuilder.buildWorkspaceFiles` renders the section last, under a heading
that states the course material, the selected code and the question win, and that
course citations must not be attached to these files. Keeping the section out of
`COURSE MATERIAL` is what preserves the citation contract.

## Workspace index

At project open `WorkspaceIndexStartup` asks `WorkspaceIndexService` to build an
index in the background: `WorkspaceIndexBuilder` walks the project content, keeps
only `java`/`kt`/`groovy` files, and stores an `OutlineExtractor` result per file
(package, type declaration, member signatures). Method bodies are never kept. The
index is serialized by `WorkspaceIndexCodec` and cached under the IDE system
directory by `WorkspaceIndexStore`, keyed by project path, so a restart does not
re-parse everything.

The index is a structure cache, not a copy of the sources. When the user ticks
**Ask in this workspace**, `WorkspaceOutlineSelector` picks at most six files whose
path, type or member names match the question and renders them under
`PROJECT OUTLINE`, after the selection and the attached files: it locates code, it
never answers for the slides.

`WorkspaceIndexService` keeps the index current with a background monitor instead
of asking the user to reload. It listens to `VirtualFileManager.VFS_CHANGES`,
collects the changed paths, and after an 800 ms debounce re-parses only those
files on a pooled thread inside a read action; the refreshed index is written back
to disk and the stale flag is cleared. Cached indexes are checked against the
project path and every indexed file's VFS modification stamp. A two-minute
`Alarm` sweep repeats that check, so a Git operation or an external edit made
while the IDE was closed cannot leave a silently stale index.

For a question, `WorkspaceOutlineSelector.rank` scores files on path, type and
member names; when nothing matches (common for Chinese questions that contain no
Java identifier) it falls back to the most recently modified files rather than
injecting nothing. The service then renders the outline plus a
`WorkspaceExcerpt` of the top two files. The index is built synchronously on the
request thread when it is missing, so ticking the option always takes effect,
even on the first question after startup.

## Editing pipeline

The edit mode turns one instruction into a reviewable file change. It is kept
separate from the teaching prompt because the reply contract is different.

`EditPromptBuilder` asks for exactly one fenced `jentor-edit` block holding the
**complete** new file, or `NO_CHANGE`, or `REFUSE: <reason>`. The rules forbid
fragments, patches, reformatting of untouched lines, placeholder comments and new
dependencies, and they state that the file body is untrusted data rather than
instructions.

`EditProposalParser` turns that reply into an `EditProposal` (path, baseline
modification stamp, complete text). Malformed, empty or missing output becomes a
refusal reason instead of an exception. The service requires the returned path to
match the user-selected target; model output cannot redirect an edit to another
file.

`EditGuard` is pure logic and owns the write rules: the same source and
build-script whitelist as the workspace context (config and data formats stay
read-only), a 200k character cap, line-ending normalisation, the no-op check and
the conflict test.

`EditReviewDialog` puts the proposal next to the file in IntelliJ's built-in
two-pane diff. The left content is a `DocumentContent` bound to the live
`Document`, so the comparison keeps following the file while the dialog is open.
A `DocumentListener` feeds `EditReviewState`: the first stamp change marks the
suggestion stale, turns the banner red and disables **Apply**, so a review can
never silently apply against a file the user has since edited.

`EditApplier` is the plugin's only write path. It resolves the file, requires a
text document, rejects read-only files, refuses when the document changed after
the suggestion was produced, skips identical content, validates Java PSI before
writing, preserves the source file's line-separator style, and replaces the whole
document inside `WriteCommandAction` so the change is one undoable command. It
never creates or deletes files and never saves silently: the built-in diff review
is expected to run first. Target selection binds a selection range only when the
selected editor file is the same target; multiple attached targets are rejected.
Edit requests expose a cancellation handle so the stop button cancels the token,
task and HTTP request.

## Conversation memory

`ChatPanel` keeps the most recent user/assistant turns. Before each request,
`PromptBuilder` trims the history to at most six messages and roughly 3000
characters, then inserts it between the system prompt and the current user
prompt. This lets follow-up questions use pronouns such as "it" or "that".

For retrieval, a short question or a question containing a pronoun also includes
the previous user question in the search query.

Completed turns are stored in `.idea/jentor/conversations`, which the repository
ignores. `ConversationStore` separates a lightweight `index.json` from one JSON
file per conversation; messages, source excerpts, disclosure text and citation
state are restored lazily. Writes use temporary files and atomic replacement.
A damaged index is rebuilt by scanning conversation files, while an unreadable
individual file is skipped. Failed, cancelled and incomplete answers are not
persisted.

## Cancellation and state isolation

Each request gets a `ChatRequest` with a request id, a `CancellationToken`, and
the submitted `Future`. The stop button cancels the token and task; the HTTP
client uses `sendAsync` and registers a cancellation callback for the HTTP
future. The UI ignores any response whose request generation is stale or whose
request was cancelled. Settings, language, Top-K, context limit, history, code
snapshot, and the `AnswerService` (and therefore the index) are captured at
request start. Index import is validated before the current index is replaced.
Failed, cancelled, or citation-failed answers are not written into persistent
history.

## IDE integration

- `CourseTaToolWindowFactory` creates the right-side **Jentor** tool window.
- `ChatPanel` owns the transcript, rounded composer, New chat action, full-height
  history drawer, settings controls and source list. The drawer sits at the left
  edge below the tool-window tabs; the conversation title and controls remain in
  the right-hand chat column.
- `MessageBody` renders paragraphs, lists, inline code, and fenced code blocks.
  Code blocks use a read-only `EditorTextField` when a file type is available,
  with a language label and copy button; prose is escaped and rendered without
  executing model HTML.
- The top bar exposes History, New chat, language and settings without mixing in
  course-library actions. Course management, refresh, and manual JSON index
  import belong to the Course materials tab. The composer footer places the
  workspace-context checkbox at bottom-left and a borderless `Mock / DeepSeek`
  selector plus a single-arrow send/stop button at bottom-right. The mode
  selector paints a rounded hover background while the pointer is over it. A
  timer keeps it in sync with settings. The attachment bar shows the selected
  line range and provides View/Remove actions.
- The composer listens to Swing `InputMethodEvent`s. While the current event
  still contains uncommitted composition text, Enter is ignored for sending so
  confirming a Chinese IME candidate cannot submit the message prematurely.
- `AskSelectionAction` is registered in `EditorPopupMenu`. It attaches the
  selected code and opens the tool window.
- `SelectionContextFactory` captures the selected text, up to 20 lines before
  and after it, the 1-based line range, and the nearest Java method/class
  signature. `PromptBuilder` labels the selected and surrounding sections and
  caps the whole IDE context at 6000 characters. The context remains attached
  until the user selects another snippet or clicks Clear.
- `TaSettings` stores the endpoint and model in XML and the API key in
  `PasswordSafe`.
- `CourseLibraryService`, `CourseDocumentSource`, `CourseDocumentParser`,
  `PdfCourseIndexBuilder`, `OcrEngine`, `CourseIndexJsonCodec` and
  `CourseLibraryStore` implement the independent multi-format
  catalog, validation and persistence.
- `CourseLibraryPanel` is the management tab for adding, refreshing, rebuilding,
  removing, locating and opening course files.
- `CourseTaService` prefers the active managed index, then a manual import, then
  `/index/course-index.json`, and runs requests on the IntelliJ application
  executor.

## Efficiency considerations

- A managed index is loaded once per application and shared by projects; its
  retriever is replaced lazily when the library revision changes.
- Retrieval is O(chunks * dimensions) with no network call.
- The bundled fallback index is about 667 KiB. POI, PDFBox and Windows
  Tess4J/Tesseract increase the plugin ZIP substantially.
- The global IDE context is capped at 16000 characters and course excerpts use
  the configurable context limit.
- The API key is never written to project files or logs.

## Known limitations

- The local vector is lexical rather than neural. It can be replaced by a real
  embedding endpoint without changing the chunk or citation contract.
- Citation validation checks document and page validity, not semantic
  attribution.
- The catalog may reference supported files from unrelated folders; importing a folder is
  only a bulk convenience.
- OCR uses bundled Windows Tess4J/Tesseract native libraries and English/Chinese
  training data. macOS/Linux OCR requires a future native distribution.
- OCR extracts text, not visual semantics, charts, diagrams or handwritten intent.
- Office tables use native row/cell structures; PDF table rows are heuristic and
  complex merged cells may not be preserved.
- Encrypted or unsupported PDFs produce an error on their own catalog row while
  other valid materials remain active.
- Moving a source PDF marks it missing; the old chunks remain until the user
  relocates or removes the catalog entry.
