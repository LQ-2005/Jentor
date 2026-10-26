# Retrieval Evaluation

The evaluation set contains ten questions with a known document and page, plus
two questions that are outside the course.

| Question | Expected source | Hit@3 |
|---|---|---|
| What is the difference between an abstract class and an interface? | Lec-2, p.30 | Yes |
| What is a checked exception? | Lec-3.1, p.5 | Yes |
| What is a generic type? | Lec-3.2, p.5 | Yes |
| What does the @Override annotation do? | Lec-3.3, p.6 | Yes |
| How do I write a JUnit test? | Lec-3.4, p.3 | Yes |
| What is passing by value? | Lec-2, p.5 | Yes |
| What is an array? | Lec-1, p.20 | Yes |
| What is BM25? | Lec-5, p.24 | Yes |
| What is the project layout of the gradebook example? | Lec-5, p.10 | Yes |
| What is a constructor? | Lec-2, p.6 | Yes |

Result: **Hit@3 = 10/10 (100%)**, **MRR = 0.800**.

Out-of-scope checks:

| Question | Max cosine | Top-chunk coverage | Material gate |
|---|---:|---:|---|
| What is the weather in Shanghai today? | 0.173 | 0.25 | Rejected |
| How do I bake a chocolate cake? | 0.098 | 0.16 | Rejected |

The coverage gate is 0.45. A rejected question takes the general-knowledge path
and starts with the required fallback sentence.

Chinese retrieval check:

- `什么是受检异常？` is expanded with the maintained glossary to
  `checked exception`.
- The expanded query retrieves `Lec-3.1-Exceptions.pdf` p.6 and p.5 in the top 3;
  the top chunk coverage is 1.0 after ignoring Chinese query terms that cannot
  occur in the English slides.

The core test suite also covers valid/invalid/missing citations, source
deduplication, the general-knowledge no-source rule, Chinese terminology
expansion, history/context prompt assembly, and retrieval quality.

Reproduce with:

```powershell
D:\anaconda\envs\deepseek_env\python.exe tools\evaluate_retrieval.py
```

The Java implementation is checked by `tools/RetrievalHarness.java` and prints the
same Hit@3 and MRR values.
