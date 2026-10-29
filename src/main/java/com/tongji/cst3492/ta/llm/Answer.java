package com.tongji.cst3492.ta.llm;

import com.tongji.cst3492.ta.model.SourceReference;
import com.tongji.cst3492.ta.model.RetrievedChunk;

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
}
