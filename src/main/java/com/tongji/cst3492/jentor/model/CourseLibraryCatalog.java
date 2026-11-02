package com.tongji.cst3492.jentor.model;

import java.util.List;

/** Persistent list of independently managed PDFs. */
public record CourseLibraryCatalog(int version, List<CourseDocumentSource> documents) {
    public CourseLibraryCatalog {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    public static CourseLibraryCatalog empty() {
        return new CourseLibraryCatalog(1, List.of());
    }
}
