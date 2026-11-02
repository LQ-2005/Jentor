# Jentor

**Enterprise Java, made clear.**

Jentor is an IntelliJ IDEA teaching assistant for CST3492 Java Enterprise
Application Development. It answers questions from the lecture material,
cites the source document and PDF page, and can reason about selected code or
workspace source files.

This is the F0 baseline required for every group.

## What is implemented

- A page-aware course index built from the nine lecture PDFs:
  **9 documents, 199 pages, 214 chunks**.
- An independently managed course-material catalog: add files or folders from
  any location in the **Course materials** tab. Supported inputs include PDF,
  DOCX/PPTX/XLSX, legacy DOC/PPT/XLS, TXT/Markdown/HTML/CSV, and PNG/JPG/BMP/TIFF
  images. Office tables keep row/cell structure; PDF/Office images and low-text
  PDF pages use bundled offline English + Simplified Chinese OCR. Each item
  records its original path, parser, location/chunk counts, tables, images, OCR
  status and errors.
- The Course materials tab title follows the configured language, the table uses
  the available tool-window height, and double-clicking a row opens its source
  file directly.
- Frequent actions remain individual top-level buttons (add files, add folder,
  refresh, open file). Rebuild, remove, show-folder, locate and copy-path live in
  both the `...` menu and the table context menu to reduce visual clutter.
- BM25-first hybrid retrieval with a hashed TF-IDF vector as a tie-breaker.
- Exact-title and phrase boosts so "what is X?" questions reach the slide that
  defines X instead of a longer overview slide.
- A grounded prompt that requires citations in the form
  `[document, <locator>]`, such as `p.12`, `pp.12-13`, `slide 4`,
  `section 3`, `sheet 2`, or `image 1`.
- Citation validation against the exact context blocks sent to the model after
  context trimming, with one automatic retry. Missing citations are also checked.
- Citation states are explicit: validated, failed, or not applicable to a
  general-knowledge answer. The UI says "document and page validated", not
  "answer correctness verified".
- Citation sections follow the Markdown headings, so a section name mentioned in
  ordinary prose or inside a code sample no longer moves the boundary, and a
  citation written in a general-supplement example is not counted as a source.
- Answer examples that cite nothing are kept apart from course-derived claims:
  the retry instructions tell the model to move course-supported statements and
  their citations into Course basis instead of deleting the citations.
- Sources contain only citations actually used and validated in the answer,
  deduplicated by document/page. Unused retrieval candidates are not exposed in
  the answer card.
- Clicking a source opens an in-IDE preview. PDF citations render the exact cited
  page without an external reader; image sources show the original image; all
  sources retain the sent excerpt, original path and open-folder actions.
- The required fallback sentence when no course material is relevant:
  `Response is based on general knowledge; no specific course material is referenced.`
  In the UI it is shown as a small gray footnote under the answer body, not as
  part of the answer text.
- Mixed course/general answers also move the fallback sentence to the gray
  footnote rather than leaving it inside the General supplement body.
- A bilingual (中文 / English) IntelliJ tool window with left/right chat bubbles,
  language selection, settings, and a direct **Clear conversation** button.
  Course material actions live only in the **Course materials** tab, not in the
  chat header. A compact `API / 离线` selector and an icon-only send/stop control
  sit at the bottom-right of the rounded composer. The
  answer language follows the selector. English mode uses the original fallback
  sentence; Chinese mode uses the equivalent Chinese sentence.
- Multi-turn conversation memory: the most recent user/assistant turns are sent
  to the LLM, and short follow-up questions use the previous user question for
  retrieval. Clear resets the conversation.
- Generation can be stopped. The request carries a cancellation token; the task
  and, where supported, the HTTP request are cancelled. Stale responses cannot
  update the UI or conversation memory.
- Answers stream into the tool window while they are generated. The client reads
  server-sent events, so the first words appear immediately instead of after the
  whole completion. The final answer replaces the streamed preview, because
  citation repair and the disclosure sentence can rewrite it. Streaming can be
  disabled in the settings, and the offline mock model streams through the same
  path, so the UI behaviour can be checked without a key.
- Answers render as Markdown with CommonMark, including GFM tables, ordered and
  nested lists, blockquotes and inline formatting. Raw HTML is escaped, unsafe
  link schemes are stripped and images are replaced by their alt text, so model
  output cannot inject markup or load remote content.
- A compact header with generation progress, a code-attachment bar, collapsible
  sources, and a "new reply" jump button. The chat header no longer duplicates
  course-material counts or Mock mode status.
- Fenced code blocks become themed rounded cards with a language label, a copy
  button, their own scrollbars and a 360 px height cap. While an answer streams
  the card is painted as plain text; the real editor component is created only
  for the final render.
- Chat prose and the input use the IDE UI font. Rendered answers can be selected
  with the mouse and copied with the platform shortcut; the right-click menu adds
  cut/copy/paste/select-all plus **Copy all**.
- Message bodies and footnotes reflow when the tool window is resized; the
  transcript has no horizontal scrollbar, and top controls wrap onto a second
  row when space is tight. User messages are right-aligned bubbles capped at 75%
  of the transcript width and sized to their content, so a short question no
  longer spans the panel.
- A right-click editor action that attaches the selected code, up to 20 lines
  before and after it, the line range, and the nearest Java method/class
  signature. The context stays attached until a new selection or Clear.
- A **Attach File to Jentor** action on the editor and the Project View that
  adds workspace source files to the next question. Only source and build-script
  files are accepted (`.java`, `.kt`, `.groovy`, `.gradle`, `.kts`). Config/data
  formats such as `.properties`, `.xml` and `.json` are refused, obvious secret
  assignments and private keys are blocked, and up to 5 files are attached. Each
  file is capped at 400 lines or 4000 characters.
- When **Ask in this workspace** is enabled in the tool window header, every
  question also carries a structural outline of the project: file paths, type
  declarations and member signatures, plus a bounded excerpt (120 lines or 1800
  characters) of the two most relevant files, so a question about code can
  actually be answered. The outline comes from an index built in the background
  when a project opens and cached on disk under the IDE system directory; it
  never contains method bodies, is capped at six files and 4000 characters, and
  is ranked by file path, type and member names, falling back to the most
  recently modified files when the question mentions no identifier.
- A background monitor keeps that index current instead of asking the user to
  reload: VFS changes are debounced, only the changed files are re-parsed and
  re-saved, and a two-minute sweep catches changes the listener missed (for
  example a Git branch switch). The answer only says the structure may be
  outdated while a refresh is still pending.
- Files are read once, when they are attached, and never re-read. The file
  section is the lowest-priority context after the course material, the selected
  code and the question, so a stale or unrelated file cannot override a grounded
  answer. Every answer that used attached files ends with a footnote saying the
  code is a snapshot and may differ from the current file.
- Explicit selected code, attached files and the optional workspace outline share
  a hard 16,000-character IDE-context budget. Workspace content is marked as
  untrusted data so instructions embedded in source comments cannot override the
  system rules.
- Settings for an OpenAI-compatible chat endpoint. The API key is stored with
  IntelliJ `PasswordSafe`, not in the project.
- An "Import course index" action for importing a rebuilt `course-index.json`.
- JUnit tests cover retrieval, citation validation, Markdown rendering and the
  streamed re-render budget; the offline CLI harness checks retrieval quality.

## Quick start

Prerequisites:

- JDK 21 to run Gradle; additionally use JDK 25 to compile against IDEA 2026.2
- IntelliJ IDEA 2025.2 through 2026.2

First copy `local.properties.example` to `local.properties` and set `ideaHome`
(IntelliJ installation root) and `jdkHome` (compiler JDK: 21 or 25 as above).
The script selects a separate compatible JDK to run Gradle; set the optional
`gradleJdkHome` to your JDK 21 root if needed. Do not commit this file. See
`SETUP.md` for Windows and macOS examples.

Commands (Windows):

```powershell
.\build-plugin.ps1
```

The first build on a machine without Gradle 8.14.2 needs network access to
download the Gradle wrapper distribution. Plugin dependencies are already in
`lib/`, so later plugin builds can run offline.

The script reads `jdkHome`, selects the Gradle runtime JDK, and runs
`buildPlugin --offline --stacktrace` with both JVM and compiler paths explicitly
configured. Gradle 8.14.2 must not run on Java 25; the compiler can still use JDK
25 while the generated plugin remains Java 21 bytecode.

`build.gradle` compiles against the IntelliJ installation configured through
`local.properties`, `IDEA_HOME`, or `-PideaHome`. It uses only Gradle's built-in
Java plugin, so the IntelliJ Platform Gradle plugin and Kotlin DSL are not
required.

Build output:

```text
build\distributions\jentor-1.3.5.zip
```

Install it into IntelliJ:

1. `Settings → Plugins`
2. Gear icon → **Install Plugin from Disk**
3. Select the ZIP above
4. Restart IntelliJ
5. Open `View → Tool Windows → Jentor`

In IntelliJ:

1. Open **Settings | Tools | Jentor**.
2. Open **Jentor → Course materials** and add supported course files from any folders.
3. Set the base URL and chat model.
4. Paste the API key.
   Streaming is enabled by default; clear **Stream the answer while it is
   generated** if the endpoint does not support server-sent events.
5. Open the **Jentor** tool window on the right.
6. Ask a course question, for example:
   `What is a checked exception?`
7. Select code in the editor, right-click, and choose
   **Ask Jentor about Selection**.
8. To add a whole file, right-click it in the editor or in the Project View and
   choose **Attach File to Jentor**. The attachment bar shows how many files
   are attached; **Remove** detaches them again.
9. Tick **Ask in this workspace** in the tool window header to add the project
   outline to every question; clear it to send course context only.

To check the UI and citation path without an API key, open the settings and
enable **Use offline mock model**. The mock model cites the first retrieved
slide and is intended only for UI verification.

## API configuration

The client calls `{baseUrl}/chat/completions`, so any OpenAI-compatible chat
endpoint works.

DeepSeek example:

```text
Base URL: https://api.deepseek.com
Chat model: deepseek-chat
```

For the Tongji AI platform, use the base URL and model name from the platform.
The bundled index does not require an embedding API.

## Managed course library

The normal workflow is:

1. Open the **Course materials** tab in the Jentor tool window.
2. Use **Add files**, drag files into the table, or use **Add folder** for bulk
   import. Files may come from unrelated directories.
3. Each file is parsed independently and appears as queued, indexing, ready,
   missing or failed.
4. Replacing a source file updates only that catalog entry. Removing an entry from
   Jentor never deletes the source file.
5. A source path that moves becomes missing and can be rebound with **Locate**.
6. Citation details show the original path and provide open-folder/copy-path
   actions.

The catalog and aggregate index are cached under the IDE system directory.
`eng`, `chi_sim` and `osd` OCR data are bundled for offline image/scanned-page
recognition. The current Tesseract native binary is Windows-only.

PDF tables use a layout heuristic; Office tables use the real document table
model. Complex PDF tables with merged cells can still lose cell boundaries.

The bundled Python-generated index remains a fallback when the managed catalog
has no usable documents. The optional Python workflow is:

```powershell
python tools\build_index.py `
  --materials "<课件目录>" `
  --output "src\main\resources\index\course-index.json"
```

On Windows, use the Python environment where `pypdf` is installed:

```powershell
python tools\build_index.py `
  --materials "<课件目录>" `
  --output "src\main\resources\index\course-index.json"
```

The in-plugin PDFBox indexer can also be run as a verification CLI:

```powershell
.\gradlew.bat runPdfIndexHarness --offline -Pmaterials="<课件目录>" `
  -Poutput="build\validation\course-index.json"
```

The manually generated JSON can still be imported with **Course materials →
Import course index**. PDFBox and pypdf can produce slightly different page text;
the managed library therefore may have a different chunk count from the bundled
214-chunk index while preserving the same document and page citations.

## Retrieval evaluation

```powershell
python tools\evaluate_retrieval.py
```

The current evaluation set contains ten questions with a known document and
page, plus two questions that are outside the course.

Current result:

- Hit@3: **10/10**
- MRR: **0.800**
- Out-of-scope coverage: **0.25** and **0.16**, both below the **0.45** gate

The Java harness prints the same result:

```powershell
.\gradlew.bat runRetrievalHarness --offline
```

Run the unit tests from the project root with
`.\gradlew.bat test`. JUnit is downloaded from Maven Central on its first run,
so that command needs network access on a fresh Gradle cache.

## Architecture

```text
PDF slides
  -> PDFBox page extraction (managed library; Python/pypdf fallback)
  -> page-bounded chunks with document/page metadata
  -> hashed TF-IDF vectors + IDF table
  -> course-index.json (bundled resource)

Question + selected code
  -> tokenizer (stopwords, plurals, identifiers)
  -> BM25 + vector tie-breaker + title/phrase bonus
  -> top-k chunks with coverage gate
  -> grounded prompt
  -> OpenAI-compatible chat endpoint (streamed)
  -> citation validator (document + page range)
  -> Markdown answer in the tool window
```

Key packages:

- `tools/build_index.py`: extraction, chunking, and index generation.
- `context/PdfCourseIndexBuilder.java`, `context/CourseLibraryStore.java`,
  `service/CourseLibraryService.java`: in-plugin PDF parsing, persistent cache,
  VFS monitoring, atomic index replacement, and fingerprint sweep.
- `index/Tokens.java`, `index/LocalEmbedder.java`, `index/Retriever.java`:
  retrieval runtime.
- `llm/PromptBuilder.java`, `llm/CitationValidator.java`,
  `llm/AnswerService.java`: grounded generation and fallback.
- `ui/ChatPanel.java`, `ui/MessageBody.java`, `ui/MarkdownContent.java`,
  `ui/CodeBlockPanel.java`, `ui/CourseTaToolWindowFactory.java`,
  `action/AskSelectionAction.java`: IDE integration and answer rendering.
- `settings/TaSettings.java`, `settings/TaConfigurable.java`: configuration.

## Current limitations

- Plugin installation and live model calls must be smoke-tested on a machine
  with IntelliJ IDEA installed and API access configured.
- The bundled index was generated from the course PDF slides.
- Offline OCR currently uses Windows Tesseract native libraries.
- OCR recognizes useful text but does not understand charts or image semantics.
- PDF table row/column recovery is heuristic; Office table structure is exact
  as exposed by Apache POI.
- If a source PDF is moved outside Jentor, it is marked missing until relocated.
- The local vector is lexical. A real embedding endpoint can be added later
  without changing the citation contract.
- Citation validation catches invalid document/page pairs, but it cannot prove
  that a sentence is a correct interpretation of the page.

## Sharing and privacy

Do not commit `local.properties`, IDE/build caches, `idea.log`, or API keys.
The API key is stored per user in IntelliJ `PasswordSafe`.

The bundled index contains text extracted from the course PDFs. Keep this
repository private and share it only with the authorized course group.

## Documentation convention

This file describes what the plugin does; `docs/architecture.md` describes how it
works. Every feature or behaviour change updates the README in the same pass as
the code:

- add one bullet under **What is implemented**, in the existing style, including
  any limit the user needs to know about;
- update **Quick start**, **API configuration** or the build section when the
  change alters commands, settings or requirements;
- record a new runtime dependency in `lib/README.md` and mention the licence
  here when it affects distribution.

## Demo script

1. Open the tool window and point out the index summary.
2. Ask `What is a checked exception?` and show the document/page citation.
3. Ask `What is the weather in Shanghai today?` and show the exact fallback
   sentence.
4. Select a method, right-click, choose **Ask Jentor about Selection**, and
   ask a question about the selected code.
5. Open **Settings** and show that the API key is not stored in the project.
6. Explain the retrieval pipeline and the 10/10 Hit@3 result.
