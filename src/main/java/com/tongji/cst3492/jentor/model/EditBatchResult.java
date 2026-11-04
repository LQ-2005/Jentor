package com.tongji.cst3492.jentor.model;

import java.util.List;

/** Complete proposals and explicit exclusions for one multi-file edit request. */
public record EditBatchResult(
        String summary,
        List<EditProposal> proposals,
        List<SkippedEditFile> skipped,
        String error) {

    public EditBatchResult {
        summary = summary == null ? "" : summary.strip();
        proposals = proposals == null ? List.of() : List.copyOf(proposals);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
        error = error == null ? "" : error.strip();
    }

    public boolean hasProposals() {
        return !proposals.isEmpty();
    }
}
