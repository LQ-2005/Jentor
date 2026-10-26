package com.tongji.cst3492.ta.llm;

import com.tongji.cst3492.ta.model.ContextBlock;

import java.util.List;

public record RagContext(String text, List<ContextBlock> blocks) {
    public RagContext {
        blocks = List.copyOf(blocks);
    }
}
