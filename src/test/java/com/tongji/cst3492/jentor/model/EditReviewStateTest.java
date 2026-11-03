package com.tongji.cst3492.jentor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditReviewStateTest {
    @Test
    void staysFreshWhileTheStampIsUnchanged() {
        EditReviewState state = new EditReviewState(42L);

        assertFalse(state.isStale());
        assertFalse(state.onSourceChanged(42L));
        assertFalse(state.isStale());
    }

    @Test
    void becomesStaleOnceWhenTheStampChanges() {
        EditReviewState state = new EditReviewState(42L);

        assertTrue(state.onSourceChanged(43L));
        assertTrue(state.isStale());
        assertFalse(state.onSourceChanged(44L), "the transition happens once");
    }

    @Test
    void ignoresTheZeroBaseline() {
        EditReviewState state = new EditReviewState(0L);

        assertFalse(state.onSourceChanged(7L));
        assertFalse(state.isStale());
    }
}
