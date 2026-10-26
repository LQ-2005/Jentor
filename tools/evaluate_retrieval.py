#!/usr/bin/env python3
"""Offline retrieval evaluation for the generated course index."""

from __future__ import annotations

import argparse
import json
import math
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_index import embed, tokenize  # noqa: E402


QUERIES = [
    ("What is the difference between an abstract class and an interface?", "Lec-2-OOP-with-Java.pdf", 30),
    ("What is a checked exception?", "Lec-3.1-Exceptions.pdf", 5),
    ("What is a generic type?", "Lec-3.2-Generics-and-Collections.pdf", 5),
    ("What does the @Override annotation do?", "Lec-3.3-ModernSyntax-Annotations-and-Reflection.pdf", 6),
    ("How do I write a JUnit test?", "Lec-3.4-JUnit.pdf", 3),
    ("What is passing by value?", "Lec-2-OOP-with-Java.pdf", 5),
    ("What is an array?", "Lec-1-Java-Fundamentals.pdf", 20),
    ("What is BM25?", "Lec-5-Building-AI-Powered-Java-Applications.pdf", 24),
    ("What is the project layout of the gradebook example?", "Lec-5-Building-AI-Powered-Java-Applications.pdf", 10),
    ("What is a constructor?", "Lec-2-OOP-with-Java.pdf", 6),
]

OUT_OF_SCOPE = [
    "What is the weather in Shanghai today?",
    "How do I bake a chocolate cake?",
]


def safe_console(text: str) -> str:
    encoding = sys.stdout.encoding or "utf-8"
    return text.encode(encoding, errors="replace").decode(encoding, errors="replace")


def cosine(left: list[float], right: list[float]) -> float:
    return sum(a * b for a, b in zip(left, right))


def prepare_bm25(chunks: list[dict]) -> tuple[list[Counter], list[int], dict[str, int], float]:
    term_frequencies: list[Counter] = []
    lengths: list[int] = []
    document_frequency: Counter[str] = Counter()
    for chunk in chunks:
        tokens = tokenize(
            f"{chunk['title']}\n{chunk['title']}\n{chunk['text']}"
        )
        frequencies = Counter(tokens)
        term_frequencies.append(frequencies)
        lengths.append(len(tokens))
        document_frequency.update(frequencies.keys())
    average_length = sum(lengths) / max(1, len(lengths))
    return term_frequencies, lengths, dict(document_frequency), average_length


def bm25_scores(
    query: str,
    chunks: list[dict],
    term_frequencies: list[Counter],
    lengths: list[int],
    document_frequency: dict[str, int],
    average_length: float,
    k1: float = 1.2,
    b: float = 0.75,
) -> list[float]:
    query_tokens = tokenize(query)
    total = len(chunks)
    scores: list[float] = []
    for frequencies, length in zip(term_frequencies, lengths):
        score = 0.0
        for token in query_tokens:
            frequency = frequencies.get(token, 0)
            if frequency == 0:
                continue
            df = document_frequency.get(token, 0)
            idf = math.log(1.0 + (total - df + 0.5) / (df + 0.5))
            denominator = frequency + k1 * (1.0 - b + b * length / average_length)
            score += idf * frequency * (k1 + 1.0) / denominator
        scores.append(score)
    return scores


def rank(scores: list[float]) -> dict[int, int]:
    order = sorted(range(len(scores)), key=lambda index: scores[index], reverse=True)
    return {index: position + 1 for position, index in enumerate(order)}


def zscore(values: list[float]) -> list[float]:
    mean = sum(values) / max(1, len(values))
    variance = sum((value - mean) ** 2 for value in values) / max(1, len(values))
    deviation = math.sqrt(variance) or 1.0
    return [(value - mean) / deviation for value in values]


def term_coverage(
    query: str,
    chunk: dict,
    idf: dict[str, float],
    default_idf: float,
) -> tuple[float, list[str], list[str]]:
    query_terms = sorted(set(tokenize(query)))
    document_terms = set(tokenize(f"{chunk['title']}\n{chunk['text']}"))
    query_terms = [term for term in query_terms if not any('\u4e00' <= c <= '\u9fff' for c in term)]
    matched = [term for term in query_terms if term in document_terms]
    total_weight = sum(idf.get(term, default_idf) for term in query_terms)
    matched_weight = sum(idf.get(term, default_idf) for term in matched)
    return (matched_weight / total_weight if total_weight else 0.0), matched, query_terms


def bigrams(tokens: list[str]) -> list[tuple[str, str]]:
    return list(zip(tokens, tokens[1:]))


def title_and_phrase_bonus(query: str, chunk: dict) -> float:
    """Reward an exact title and query bigrams found in the slide title/text."""
    query_tokens = tokenize(query)
    title_tokens = tokenize(chunk["title"])
    text_tokens = tokenize(chunk["text"])
    score = 0.0
    if title_tokens == query_tokens:
        score += 1.0
    elif set(query_tokens).issubset(set(title_tokens)):
        score += 0.6
    for pair in bigrams(query_tokens):
        if pair in bigrams(title_tokens):
            score += 0.4
        elif pair in bigrams(text_tokens):
            score += 0.2
    if len(query_tokens) == 1:
        if query_tokens[0] in title_tokens:
            score += 0.5
        elif query_tokens[0] in text_tokens:
            score += 0.25
    return score


def evaluate(index_path: Path, top_k: int) -> None:
    payload = json.loads(index_path.read_text(encoding="utf-8"))
    chunks = payload["chunks"]
    idf = {token: float(value) for token, value in payload["idf"].items()}
    default_idf = float(payload["defaultIdf"])
    term_frequencies, lengths, document_frequency, average_length = prepare_bm25(chunks)

    hits = 0
    reciprocal_rank = 0.0
    threshold = 0.45
    print(f"Chunks: {len(chunks)}; top-k: {top_k}\n")
    for query, expected_file, expected_page in QUERIES:
        query_vector = embed(query, idf, default_idf)
        vector_scores = [cosine(query_vector, chunk["vector"]) for chunk in chunks]
        lexical_scores = bm25_scores(
            query, chunks, term_frequencies, lengths, document_frequency, average_length
        )
        normalized_vector = zscore(vector_scores)
        normalized_lexical = zscore(lexical_scores)
        fused = [
            0.15 * normalized_vector[index]
            + 0.85 * normalized_lexical[index]
            + 0.75 * title_and_phrase_bonus(query, chunks[index])
            for index in range(len(chunks))
        ]
        order = sorted(range(len(chunks)), key=lambda index: fused[index], reverse=True)[:top_k]
        coverage, matched, query_terms = term_coverage(
            query, chunks[order[0]], idf, default_idf
        )
        has_material = coverage >= threshold
        matched = any(
            chunks[index]["fileName"] == expected_file
            and chunks[index]["pageStart"] <= expected_page <= chunks[index]["pageEnd"]
            for index in order
        )
        hit = has_material and matched
        hits += int(hit)
        for position, index in enumerate(order, start=1):
            if (
                chunks[index]["fileName"] == expected_file
                and chunks[index]["pageStart"] <= expected_page <= chunks[index]["pageEnd"]
            ):
                reciprocal_rank += 1.0 / position
                break
        print(f"Q: {query}")
        print(
            f"   expected: {expected_file} p.{expected_page}; hit@{top_k}={hit}; "
            f"coverage={coverage:.2f}; material={has_material}"
        )
        for index in order:
            chunk = chunks[index]
            marker = (
                "*"
                if chunk["fileName"] == expected_file
                and chunk["pageStart"] <= expected_page <= chunk["pageEnd"]
                else " "
            )
            print(
                f"   {marker} {chunk['fileName']} p.{chunk['pageStart']}"
                f" | cos={vector_scores[index]:.3f}"
                f" | bm25={lexical_scores[index]:.3f}"
                f" | {safe_console(chunk['text'][:70].replace(chr(10), ' / '))}"
            )
        print()

    print(f"Hit@{top_k}: {hits}/{len(QUERIES)} = {hits / len(QUERIES):.0%}")
    print(f"MRR: {reciprocal_rank / len(QUERIES):.3f}")
    print("\nOut-of-scope checks (max cosine):")
    for query in OUT_OF_SCOPE:
        query_vector = embed(query, idf, default_idf)
        vector_scores = [cosine(query_vector, chunk["vector"]) for chunk in chunks]
        lexical_scores = bm25_scores(
            query, chunks, term_frequencies, lengths, document_frequency, average_length
        )
        normalized_vector = zscore(vector_scores)
        normalized_lexical = zscore(lexical_scores)
        fused = [
            0.15 * normalized_vector[index]
            + 0.85 * normalized_lexical[index]
            + 0.75 * title_and_phrase_bonus(query, chunks[index])
            for index in range(len(chunks))
        ]
        top_index = max(range(len(chunks)), key=lambda index: fused[index])
        coverage, matched, query_terms = term_coverage(
            query, chunks[top_index], idf, default_idf
        )
        best_vector = max(vector_scores)
        best_lexical = max(lexical_scores)
        print(
            f"   {query} | max_cos={best_vector:.3f} | max_bm25={best_lexical:.3f} "
            f"| coverage={coverage:.2f} | material={coverage >= 0.45}"
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--index",
        type=Path,
        default=Path(__file__).resolve().parents[1]
        / "src"
        / "main"
        / "resources"
        / "index"
        / "course-index.json",
    )
    parser.add_argument("--top-k", type=int, default=3)
    args = parser.parse_args()
    evaluate(args.index, args.top_k)


if __name__ == "__main__":
    main()
