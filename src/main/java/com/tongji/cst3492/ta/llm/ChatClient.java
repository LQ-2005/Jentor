package com.tongji.cst3492.ta.llm;

import java.io.IOException;
import java.util.List;

public interface ChatClient {
    String complete(
            LlmConfig config,
            List<ChatMessage> messages,
            CancellationToken token) throws IOException;
}
