# Architecture

## Goal

Provide a course-specific teaching assistant inside IntelliJ IDEA. The assistant
must retrieve CST3492 lecture material, answer with document and page citations,
and explicitly fall back to general Java knowledge when the slides do not cover
the question.

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

The indexer is `tools/build_index.py`.

- PDF text is extracted page by page with pypdf.
- Two extraction modes are compared. The layout mode preserves code indentation
  but animated slides can produce text such as `Pa s s i n g`. If the layout
  result has a high ratio of single-letter tokens, the default extraction is used
  instead.
- Footer page numbers are removed when they can be identified safely.
- Chunks never cross a page boundary. A page up to 900 characters becomes one
  chunk; longer pages are split at line boundaries with a 120-character overlap.
- Every chunk stores `docId`, `docTitle`, `fileName`, `pageStart`, `pageEnd`,
  `locatorType`, `chunkIndex`, `title`, `text`, and a 512-dimensional vector.

The generated index currently contains 9 documents, 199 pages, and 214 chunks.

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
- citations in the form `[document, p.12]` or `[document, Slide 12]`;
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
candidates are shown only in a collapsible debug section. Clicking a source
opens the document name, PDF page, and the actual excerpt sent in this request.
The course index uses PDF page numbers, so citations use `p.`/`pp.` rather than
mixing `Slide`.

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
current conversation, and `Clear`/stop drop the buffer immediately. The same
streaming path is used by `MockChatClient`, which splits its deterministic answer
into small chunks so the behaviour is testable offline.

Streaming can be switched off in the settings; `CourseTaService` then passes
`StreamSink.NONE`, `AnswerService` falls back to `ChatClient.complete`, and the
answer appears in one piece as before.

## Conversation memory

`ChatPanel` keeps the most recent user/assistant turns. Before each request,
`PromptBuilder` trims the history to at most six messages and roughly 3000
characters, then inserts it between the system prompt and the current user
prompt. This lets follow-up questions use pronouns such as "it" or "that".

For retrieval, a short question or a question containing a pronoun also includes
the previous user question in the search query. `Clear` resets both the visible
chat and the in-memory history. History is intentionally in-memory for F0, so a
tool-window recreation or IDE restart starts a new conversation.

## Cancellation and state isolation

Each request gets a `ChatRequest` with a request id, a `CancellationToken`, and
the submitted `Future`. The stop button cancels the token and task; the HTTP
client uses `sendAsync` and registers a cancellation callback for the HTTP
future. The UI ignores any response whose request generation is stale or whose
request was cancelled. Settings, language, Top-K, context limit, history, code
snapshot, and the `AnswerService` (and therefore the index) are captured at
request start. Index import is validated before the current index is replaced.
Failed, cancelled, or citation-failed answers are not written into conversation
memory.

## IDE integration

- `CourseTaToolWindowFactory` creates the right-side **Course TA** tool window.
- `ChatPanel` owns the transcript, input, send/clear/settings controls, index
  summary, and source list.
- `MessageBody` renders paragraphs, lists, inline code, and fenced code blocks.
  Code blocks use a read-only `EditorTextField` when a file type is available,
  with a language label and copy button; prose is escaped and rendered without
  executing model HTML.
- The top bar exposes language, settings, and a more menu for course materials,
  index import, and clearing the conversation. A timer keeps the Mock badge in
  sync with settings. The attachment bar shows the selected line range and
  provides View/Remove actions.
- `AskSelectionAction` is registered in `EditorPopupMenu`. It attaches the
  selected code and opens the tool window.
- `SelectionContextFactory` captures the selected text, up to 20 lines before
  and after it, the 1-based line range, and the nearest Java method/class
  signature. `PromptBuilder` labels the selected and surrounding sections and
  caps the whole IDE context at 6000 characters. The context remains attached
  until the user selects another snippet or clicks Clear.
- `TaSettings` stores the endpoint and model in XML and the API key in
  `PasswordSafe`.
- `CourseTaService` loads the bundled index from
  `/index/course-index.json` and runs requests on the IntelliJ application
  executor.

## Efficiency considerations

- The index is loaded once per project and kept in memory.
- Retrieval is O(chunks * dimensions) with no network call.
- The index is 667 KiB and the full corpus is under 100k characters.
- The LLM context is capped at 6000 characters and the selected-code context at
  4000 characters.
- The API key is never written to project files or logs.

## Known limitations

- The local vector is lexical rather than neural. It can be replaced by a real
  embedding endpoint without changing the chunk or citation contract.
- Citation validation checks document and page validity, not semantic
  attribution.
- Runtime PDF parsing is delegated to the Python indexer; the plugin imports the
  generated JSON.
