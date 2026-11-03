package com.tongji.cst3492.jentor.model;

/**
 * One AI-proposed edit of a single workspace file: the target path, the
 * modification stamp of the document the suggestion was produced from, and the
 * complete new content. Never a patch, never a fragment.
 */
public record EditProposal(
        String path,
        long baseModificationStamp,
        String newText,
        String instruction) {

    public EditProposal {
        path = path == null ? "" : path.replace('\\', '/').strip();
        newText = newText == null ? "" : newText;
        instruction = instruction == null ? "" : instruction;
    }
}
