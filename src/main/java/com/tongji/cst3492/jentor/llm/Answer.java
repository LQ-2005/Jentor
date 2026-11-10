package com.tongji.cst3492.jentor.llm;

import com.tongji.cst3492.jentor.model.SourceReference;
import com.tongji.cst3492.jentor.model.RetrievedChunk;

import java.util.List;

public record Answer(
        String text,
        List<SourceReference> sources,
        List<RetrievedChunk> debugCandidates,
        boolean grounded,
        CitationStatus citationStatus,
        List<String> warnings,
        String disclosure,
        String contextNote) {
    public Answer {
        sources = List.copyOf(sources);
        debugCandidates = List.copyOf(debugCandidates);
        warnings = List.copyOf(warnings);
        disclosure = disclosure == null ? "" : disclosure;
        contextNote = contextNote == null ? "" : contextNote;
    }

    /**
     * True when cited course sections are valid but the answer also contains an
     * explicitly disclosed general-knowledge supplement.
     */
    public boolean hasMixedEvidence() {
        return citationStatus == CitationStatus.VALIDATED
                && !sources.isEmpty()
                && !disclosure.isBlank();
    }
}
