package com.tongji.cst3492.ta.llm;

/**
 * Receives answer text while a completion is still being generated.
 *
 * <p>{@link #append(String)} may be called from a background thread, so the IDE
 * layer must marshal the text to its own thread. {@link #reset()} is called when
 * the visible text has to be discarded and regenerated, for example after a
 * citation retry. The final {@link Answer#text()} always remains authoritative:
 * the UI replaces the streamed preview with it when the request completes.</p>
 */
public interface StreamSink {
    /** Text that is already the answer shown to the user. */
    StreamSink NONE = new StreamSink() {
        @Override
        public void append(String delta) {
            // Streaming is disabled or the client does not support it.
        }
    };

    /** Appends a non-empty piece of answer text. */
    void append(String delta);

    /** Clears the text received so far because the answer is being regenerated. */
    default void reset() {
        // Most sinks keep no text of their own and need no reset.
    }
}
