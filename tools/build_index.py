#!/usr/bin/env python3
"""Build a page-aware course index for the Jentor plugin.

The index keeps the original PDF page number for every chunk. Retrieval uses a
local hashed term-frequency vector plus BM25 in the Java runtime, so the plugin
can work without an embedding service. An OpenAI-compatible embedding service
can be added later without changing the chunk format.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import unicodedata
from collections import Counter
from pathlib import Path

DIMENSIONS = 512
MAX_CHARS = 900
OVERLAP_CHARS = 120

TOKEN_RE = re.compile(r"[A-Za-z0-9_]+|[\u4e00-\u9fff]")
CJK_RE = re.compile(r"[\u4e00-\u9fff]")
CAMEL_RE = re.compile(r"(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")
LETTER_DIGIT_RE = re.compile(r"[A-Za-z]+|[0-9]+")

STOPWORDS = {
    "a", "about", "above", "after", "again", "against", "all", "am", "an",
    "and", "any", "are", "as", "at", "be", "because", "been", "before",
    "being", "below", "between", "both", "but", "by", "can", "could", "did",
    "do", "does", "doing", "down", "during", "each", "few", "for", "from",
    "further", "had", "has", "have", "having", "he", "her", "here", "hers",
    "herself", "him", "himself", "his", "how", "i", "if", "in", "into", "is",
    "it", "its", "itself", "just", "me", "more", "most", "my", "myself", "no",
    "nor", "not", "now", "of", "off", "on", "once", "only", "or", "other",
    "our", "ours", "ourselves", "out", "over", "own", "same", "she", "should",
    "so", "some", "such", "than", "that", "the", "their", "theirs", "them",
    "themselves", "then", "there", "these", "they", "this", "those", "through",
    "to", "too", "under", "until", "up", "very", "vs", "was", "we", "were", "what",
    "when", "where", "which", "while", "who", "whom", "why", "will", "with",
    "would", "you", "your", "yours", "yourself", "yourselves",
}

# Java keywords and common code terms remain searchable even if they also occur
# in the stopword list.
CODE_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "if", "implements", "import",
    "instanceof", "int", "interface", "long", "new", "package", "private",
    "protected", "public", "return", "short", "static", "strictfp", "super",
    "switch", "synchronized", "this", "throw", "throws", "transient", "try",
    "void", "volatile", "while", "record", "sealed", "permits", "yield", "var",
}


def clean_text(text: str, page_number: int) -> str:
    """Remove slide footers while keeping code indentation and line breaks."""
    text = text.replace("\u00a0", " ")
    lines: list[str] = []
    for raw_line in text.splitlines():
        line = raw_line.rstrip()
        if re.fullmatch(rf"\s*{page_number}\s*", line):
            continue
        if re.search(rf"\s{{2,}}{page_number}\s*$", line) and len(line) < 160:
            continue
        lines.append(line)
    text = "\n".join(lines)
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = text.strip()
    suffix = str(page_number)
    if text.endswith(suffix) and len(text) > len(suffix):
        prefix = text[: -len(suffix)]
        if prefix[-1].isalpha() or prefix[-1] in ")]\"'":
            text = prefix.rstrip()
    return text


def extract_pages(pdf_path: Path) -> list[tuple[int, str]]:
    try:
        from pypdf import PdfReader
    except ModuleNotFoundError as exc:
        raise SystemExit(
            "pypdf is required to rebuild the index: "
            "install it with 'python -m pip install pypdf'."
        ) from exc

    reader = PdfReader(str(pdf_path))
    pages: list[tuple[int, str]] = []
    for page_number, page in enumerate(reader.pages, start=1):
        layout_text = clean_text(
            page.extract_text(extraction_mode="layout") or "",
            page_number,
        )
        default_text = clean_text(page.extract_text() or "", page_number)
        cleaned = choose_page_text(layout_text, default_text)
        if cleaned:
            pages.append((page_number, cleaned))
    return pages


def single_letter_ratio(text: str) -> float:
    tokens = TOKEN_RE.findall(text)
    if not tokens:
        return 0.0
    single = sum(1 for token in tokens if len(token) == 1 and token.isalpha())
    return single / len(tokens)


def choose_page_text(layout_text: str, default_text: str) -> str:
    """Pick the extraction that best preserves readable words.

    Animated slides sometimes put every glyph in its own text run. The layout
    extraction then turns "Passing" into "Pa s s i n g", while the default
    extraction keeps words together. For code-heavy pages the layout extraction
    is still better because it preserves indentation and line breaks.
    """
    if not layout_text:
        return default_text
    if not default_text:
        return layout_text
    layout_ratio = single_letter_ratio(layout_text)
    default_ratio = single_letter_ratio(default_text)
    if layout_ratio > 0.12 and default_ratio < layout_ratio * 0.5:
        return default_text
    if len(layout_text.splitlines()) >= 3 and len(default_text.splitlines()) <= 1:
        return layout_text
    return layout_text


def page_title(text: str) -> str:
    for line in text.splitlines():
        title = line.strip(" \t•-–")
        if title:
            return title[:160]
    return ""


def split_page(text: str) -> list[str]:
    """Split one page into chunks without crossing page boundaries."""
    if len(text) <= MAX_CHARS:
        return [text]

    chunks: list[str] = []
    start = 0
    while start < len(text):
        end = min(start + MAX_CHARS, len(text))
        if end < len(text):
            newline = text.rfind("\n", start + int(MAX_CHARS * 0.6), end)
            if newline > start:
                end = newline
        chunk = text[start:end].strip()
        if chunk:
            chunks.append(chunk)
        if end >= len(text):
            break
        start = max(end - OVERLAP_CHARS, start + 1)
    return chunks


def tokenize(text: str) -> list[str]:
    """Tokenize English identifiers and CJK text identically in Python and Java."""
    normalized = unicodedata.normalize("NFKC", text)
    tokens: list[str] = []
    previous_cjk: str | None = None
    for match in TOKEN_RE.finditer(normalized):
        raw = match.group(0)
        if CJK_RE.fullmatch(raw):
            tokens.append(raw)
            if previous_cjk is not None:
                tokens.append(previous_cjk + raw)
            previous_cjk = raw
            continue

        previous_cjk = None
        lower = raw.lower()
        tokens.append(lower)

        camel_parts = [part for part in CAMEL_RE.split(raw) if part]
        if len(camel_parts) > 1:
            tokens.extend(part.lower() for part in camel_parts)

        underscore_parts = [part for part in raw.split("_") if part]
        if len(underscore_parts) > 1:
            tokens.extend(part.lower() for part in underscore_parts)

        letter_digit_parts = LETTER_DIGIT_RE.findall(raw)
        if len(letter_digit_parts) > 1:
            tokens.extend(part.lower() for part in letter_digit_parts)
    return [
        stem(token)
        for token in tokens
        if token not in STOPWORDS or token in CODE_KEYWORDS
        if not (
            len(token) == 1
            and token.isalpha()
            and not CJK_RE.fullmatch(token)
        )
    ]


def stem(token: str) -> str:
    """Light plural stemming; keeps Java keywords untouched."""
    if token in CODE_KEYWORDS or len(token) < 5 or not token.isalpha():
        return token
    if token.endswith("ies") and len(token) > 4:
        return token[:-3] + "y"
    if token.endswith("es") and len(token) > 4:
        without_es = token[:-2]
        if without_es.endswith(("ss", "x", "ch", "sh", "z")):
            return without_es
        return token[:-1]
    if (
        token.endswith("s")
        and not token.endswith("ss")
        and not token.endswith("us")
        and not token.endswith("is")
    ):
        return token[:-1]
    return token


def hash_token(token: str) -> tuple[int, float]:
    digest = hashlib.sha256(token.encode("utf-8")).digest()
    value = int.from_bytes(digest[:8], "big", signed=False)
    index = value % DIMENSIONS
    sign = 1.0 if ((value >> 63) & 1) == 0 else -1.0
    return index, sign


def embed(text: str, idf: dict[str, float], default_idf: float) -> list[float]:
    counts = Counter(tokenize(text))
    vector = [0.0] * DIMENSIONS
    for token, count in counts.items():
        index, sign = hash_token(token)
        weight = (1.0 + math.log(count)) * idf.get(token, default_idf)
        vector[index] += sign * weight
    norm = math.sqrt(sum(value * value for value in vector))
    if norm > 0:
        vector = [round(value / norm, 6) for value in vector]
    return vector


def slugify(name: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    return slug or "document"


def build(materials_dir: Path, output_path: Path) -> None:
    pdfs = sorted(materials_dir.glob("*.pdf"))
    if not pdfs:
        raise SystemExit(f"No PDF files found in {materials_dir}")

    documents: list[dict[str, object]] = []
    raw_chunks: list[dict[str, object]] = []

    for pdf_path in pdfs:
        pages = extract_pages(pdf_path)
        document_id = slugify(pdf_path.stem)
        documents.append(
            {
                "id": document_id,
                "title": pdf_path.stem,
                "fileName": pdf_path.name,
                "pageCount": len(pages),
            }
        )
        for page_number, page_text in pages:
            title = page_title(page_text)
            for chunk_index, chunk_text in enumerate(split_page(page_text)):
                raw_chunks.append(
                    {
                        "id": f"{document_id}-p{page_number}-c{chunk_index}",
                        "docId": document_id,
                        "docTitle": pdf_path.stem,
                        "fileName": pdf_path.name,
                        "locatorType": "PAGE",
                        "pageStart": page_number,
                        "pageEnd": page_number,
                        "chunkIndex": chunk_index,
                        "title": title,
                        "text": chunk_text,
                    }
                )

    chunk_count = len(raw_chunks)
    document_frequency: Counter[str] = Counter()
    chunk_tokens: list[list[str]] = []
    for chunk in raw_chunks:
        tokens = tokenize(f"{chunk['title']}\n{chunk['title']}\n{chunk['text']}")
        chunk_tokens.append(tokens)
        document_frequency.update(set(tokens))

    idf = {
        token: round(math.log((chunk_count + 1) / (frequency + 1)) + 1.0, 6)
        for token, frequency in document_frequency.items()
    }
    default_idf = round(math.log(chunk_count + 1) + 1.0, 6)

    for chunk in raw_chunks:
        chunk["vector"] = embed(
            f"{chunk['title']}\n{chunk['title']}\n{chunk['text']}",
            idf,
            default_idf,
        )

    payload = {
        "version": 1,
        "embeddingModel": "local-sha256-tfidf",
        "dimensions": DIMENSIONS,
        "chunkCount": chunk_count,
        "defaultIdf": default_idf,
        "idf": idf,
        "documents": documents,
        "chunks": raw_chunks,
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    print(f"Documents: {len(documents)}")
    print(f"Pages: {sum(int(document['pageCount']) for document in documents)}")
    print(f"Chunks: {chunk_count}")
    print(f"Vocabulary: {len(idf)}")
    print(f"Wrote: {output_path} ({output_path.stat().st_size / 1024:.1f} KiB)")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--materials",
        type=Path,
        default=Path(__file__).resolve().parents[2] / "materials",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parents[1]
        / "src"
        / "main"
        / "resources"
        / "index"
        / "course-index.json",
    )
    args = parser.parse_args()
    build(args.materials.resolve(), args.output.resolve())


if __name__ == "__main__":
    main()
