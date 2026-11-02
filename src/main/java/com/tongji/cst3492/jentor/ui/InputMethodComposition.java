package com.tongji.cst3492.jentor.ui;

import java.text.AttributedCharacterIterator;

/** Determines whether an InputMethodEvent still contains uncommitted text. */
final class InputMethodComposition {
    private InputMethodComposition() {
    }

    static boolean hasUncommittedText(
            AttributedCharacterIterator text, int committedCharacterCount) {
        return text != null && committedCharacterCount < text.getEndIndex();
    }
}
