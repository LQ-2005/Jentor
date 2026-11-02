package com.tongji.cst3492.jentor.ui;

import org.junit.jupiter.api.Test;

import java.text.AttributedString;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputMethodCompositionTest {
    @Test
    void uncommittedTextBlocksEnterSending() {
        AttributedString text = new AttributedString("shang");

        assertTrue(InputMethodComposition.hasUncommittedText(
                text.getIterator(), 0));
        assertTrue(InputMethodComposition.hasUncommittedText(
                text.getIterator(), 2));
    }

    @Test
    void committedOrMissingTextAllowsEnterSending() {
        AttributedString text = new AttributedString("上海");

        assertFalse(InputMethodComposition.hasUncommittedText(
                text.getIterator(), text.getIterator().getEndIndex()));
        assertFalse(InputMethodComposition.hasUncommittedText(null, 0));
    }
}
