package com.tongji.cst3492.jentor.llm;

import java.io.IOException;
import java.util.List;

public interface ChatClient {
    String complete(
            LlmConfig config,
            List<ChatMessage> messages,
            CancellationToken token) throws IOException;

    /** Whether {@link #stream} can deliver text incrementally. */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * Generates the answer and reports the text incrementally through the sink.
     *
     * <p>The default implementation asks for the whole answer and forwards it in
     * one piece, so every client works with the streaming UI path even when the
     * provider does not support server-sent events.</p>
     *
     * @return the full answer text, identical to what the sink received
     */
    default String stream(
            LlmConfig config,
            List<ChatMessage> messages,
            CancellationToken token,
            StreamSink sink) throws IOException {
        String text = complete(config, messages, token);
        if (!text.isEmpty()) {
            sink.append(text);
        }
        return text;
    }
}
