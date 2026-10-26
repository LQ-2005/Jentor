# CST3492 Course TA

An IntelliJ IDEA plugin that answers questions about the CST3492 Java Enterprise
Application Development lecture slides and cites the source document and page.

This is the F0 baseline required for every group.

## What is implemented

- A page-aware course index built from the nine lecture PDFs:
  **9 documents, 199 pages, 214 chunks**.
- BM25-first hybrid retrieval with a hashed TF-IDF vector as a tie-breaker.
- Exact-title and phrase boosts so "what is X?" questions reach the slide that
  defines X instead of a longer overview slide.
- A grounded prompt that requires citations in the form
  `[document, p.12]` or `[document, pp.12-13]`. The index uses PDF page numbers;
  `Slide` is not mixed into citations.
- Citation validation against the exact context blocks sent to the model after
  context trimming, with one automatic retry. Missing citations are also checked.
- Citation states are explicit: validated, failed, or not applicable to a
  general-knowledge answer. The UI says "document and page validated", not
  "answer correctness verified".
- Sources contain only citations actually used and validated in the answer,
  deduplicated by document/page. Unused retrieval candidates appear only in a
  collapsible debug section.
- The required fallback sentence when no course material is relevant:
  `Response is based on general knowledge; no specific course material is referenced.`
  In the UI it is shown as a small gray footnote under the answer body, not as
  part of the answer text.
- A bilingual (中文 / English) IntelliJ tool window with left/right chat bubbles,
  an index summary, language selection, and send/clear/settings controls. The
  answer language follows the selector. English mode uses the original fallback
  sentence; Chinese mode uses the equivalent Chinese sentence.
- Multi-turn conversation memory: the most recent user/assistant turns are sent
  to the LLM, and short follow-up questions use the previous user question for
  retrieval. Clear resets the conversation.
- Generation can be stopped. The request carries a cancellation token; the task
  and, where supported, the HTTP request are cancelled. Stale responses cannot
  update the UI or conversation memory.
- A compact top bar with course-material details, a more menu, a persistent Mock
  badge, a code-attachment bar, collapsible sources, code blocks with copy
  buttons, copy-answer, and a "new reply" jump button.
- Chat prose and the input use the IDE UI font. Read-only message text and the
  input provide right-click cut/copy/paste/select-all menus.
- Message bodies and footnotes reflow when the tool window is resized; the
  transcript has no horizontal scrollbar, and top controls wrap onto a second
  row when space is tight.
- A right-click editor action that attaches the selected code, up to 20 lines
  before and after it, the line range, and the nearest Java method/class
  signature. The context stays attached until a new selection or Clear.
- Settings for an OpenAI-compatible chat endpoint. The API key is stored with
  IntelliJ `PasswordSafe`, not in the project.
- An "Import course index" action for importing a rebuilt `course-index.json`.
- Unit tests and an offline CLI harness for the RAG core.

## Quick start

Prerequisites:

- JDK 21
- IntelliJ IDEA 2025.2 through 2026.1

First copy `local.properties.example` to `local.properties` and set `ideaHome`
(IntelliJ installation root) and `jdkHome` (JDK 21 root). Do not commit this
file. See `SETUP.md` for Windows and macOS examples.

Commands (Windows):

```powershell
.\build-plugin.ps1
```

The first build on a machine without Gradle 8.14.2 needs network access to
download the Gradle wrapper distribution. Plugin dependencies are already in
`lib/`, so later plugin builds can run offline.

The script reads `jdkHome` and runs:

```powershell
.\gradlew.bat buildPlugin --offline --stacktrace
```

`build.gradle` compiles against the IntelliJ installation configured through
`local.properties`, `IDEA_HOME`, or `-PideaHome`. It uses only Gradle's built-in
Java plugin, so the IntelliJ Platform Gradle plugin and Kotlin DSL are not
required.

Build output:

```text
build\distributions\cst3492-course-ta-0.4.3.zip
```

Install it into IntelliJ:

1. `Settings → Plugins`
2. Gear icon → **Install Plugin from Disk**
3. Select the ZIP above
4. Restart IntelliJ
5. Open `View → Tool Windows → Course TA`

In IntelliJ:

1. Open **Settings | Tools | CST3492 Course TA**.
2. Set the base URL and chat model.
3. Paste the API key.
4. Open the **Course TA** tool window on the right.
5. Ask a course question, for example:
   `What is a checked exception?`
6. Select code in the editor, right-click, and choose
   **Ask Course TA about Selection**.

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

## Rebuild the course index

The indexer needs `pypdf`.

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

After rebuilding, either rebuild the plugin or use **Course materials → Import
course index** in the tool window and select the new JSON file.

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
  -> pypdf page extraction (default or layout mode, whichever is readable)
  -> page-bounded chunks with document/page metadata
  -> hashed TF-IDF vectors + IDF table
  -> course-index.json (bundled resource)

Question + selected code
  -> tokenizer (stopwords, plurals, identifiers)
  -> BM25 + vector tie-breaker + title/phrase bonus
  -> top-k chunks with coverage gate
  -> grounded prompt
  -> OpenAI-compatible chat endpoint
  -> citation validator (document + page range)
  -> tool window answer
```

Key packages:

- `tools/build_index.py`: extraction, chunking, and index generation.
- `index/Tokens.java`, `index/LocalEmbedder.java`, `index/Retriever.java`:
  retrieval runtime.
- `llm/PromptBuilder.java`, `llm/CitationValidator.java`,
  `llm/AnswerService.java`: grounded generation and fallback.
- `ui/ChatPanel.java`, `ui/CourseTaToolWindowFactory.java`,
  `action/AskSelectionAction.java`: IDE integration.
- `settings/TaSettings.java`, `settings/TaConfigurable.java`: configuration.

## Current limitations

- Plugin installation and live model calls must be smoke-tested on a machine
  with IntelliJ IDEA installed and API access configured.
- The bundled index was generated from the course PDF slides.
- Runtime PDF reindexing is performed by the Python script; the plugin imports
  the generated JSON.
- The local vector is lexical. A real embedding endpoint can be added later
  without changing the citation contract.
- Citation validation catches invalid document/page pairs, but it cannot prove
  that a sentence is a correct interpretation of the page.

## Sharing and privacy

Do not commit `local.properties`, IDE/build caches, `idea.log`, or API keys.
The API key is stored per user in IntelliJ `PasswordSafe`.

The bundled index contains text extracted from the course PDFs. Keep this
repository private and share it only with the authorized course group.

## Demo script

1. Open the tool window and point out the index summary.
2. Ask `What is a checked exception?` and show the document/page citation.
3. Ask `What is the weather in Shanghai today?` and show the exact fallback
   sentence.
4. Select a method, right-click, choose **Ask Course TA about Selection**, and
   ask a question about the selected code.
5. Open **Settings** and show that the API key is not stored in the project.
6. Explain the retrieval pipeline and the 10/10 Hit@3 result.
