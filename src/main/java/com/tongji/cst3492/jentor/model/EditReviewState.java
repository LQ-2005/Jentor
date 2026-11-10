package com.tongji.cst3492.jentor.model;

/**
 * Tracks whether the source file changed while the review dialog is open. The
 * diff viewer keeps the left pane live, this state only decides the banner and
 * whether applying is still allowed.
 */
public final class EditReviewState {
    private final long baseModificationStamp;
    private final String baseContentHash;
    private boolean stale;

    public EditReviewState(long baseModificationStamp) {
        this(baseModificationStamp, "");
    }

    public EditReviewState(long baseModificationStamp, String baseContentHash) {
        this.baseModificationStamp = baseModificationStamp;
        this.baseContentHash = baseContentHash == null ? "" : baseContentHash;
    }

    public EditReviewState(long baseModificationStamp, long currentModificationStamp) {
        this(baseModificationStamp);
        onSourceChanged(currentModificationStamp);
    }

    public EditReviewState(
            long baseModificationStamp,
            long currentModificationStamp,
            String baseContentHash,
            String currentContentHash) {
        this(baseModificationStamp, baseContentHash);
        onSourceChanged(currentModificationStamp, currentContentHash);
    }

    /** @return true on the transition to stale, so callers refresh the UI once. */
    public boolean onSourceChanged(long currentModificationStamp) {
        return onSourceChanged(currentModificationStamp, "");
    }

    public boolean onSourceChanged(
            long currentModificationStamp, String currentContentHash) {
        if (stale || baseModificationStamp <= 0) {
            return false;
        }
        if (currentModificationStamp == baseModificationStamp) {
            return false;
        }
        if (!baseContentHash.isBlank()
                && baseContentHash.equals(currentContentHash)) {
            return false;
        }
        stale = true;
        return true;
    }

    public boolean isStale() {
        return stale;
    }

    public long baseModificationStamp() {
        return baseModificationStamp;
    }
}
