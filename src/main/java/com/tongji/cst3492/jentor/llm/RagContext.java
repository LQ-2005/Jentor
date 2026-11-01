package com.tongji.cst3492.jentor.llm;

import com.tongji.cst3492.jentor.model.ContextBlock;

import java.util.List;

public record RagContext(String text, List<ContextBlock> blocks) {
    public RagContext {
        blocks = List.copyOf(blocks);
    }
}
