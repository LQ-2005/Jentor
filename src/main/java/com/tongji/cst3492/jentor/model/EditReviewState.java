package com.tongji.cst3492.jentor.model;

/**
 * Tracks whether the source file changed while the review dialog is open. The
 * diff viewer keeps the left pane live, this state only decides the banner and
 * whether applying is still allowed.
 */
public final class EditReviewState {
    private final long baseModificationStamp;
    private boolean stale;

    public EditReviewState(long baseModificationStamp) {
        this.baseModificationStamp = baseModificationStamp;
    }

    /** @return true on the transition to stale, so callers refresh the UI once. */
    public boolean onSourceChanged(long currentModificationStamp) {
        if (stale || baseModificationStamp <= 0) {
            return false;
        }
        if (currentModificationStamp == baseModificationStamp) {
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
