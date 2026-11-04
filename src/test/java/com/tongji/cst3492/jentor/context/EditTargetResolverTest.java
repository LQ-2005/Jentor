package com.tongji.cst3492.jentor.context;

import com.tongji.cst3492.jentor.model.WorkspaceIndex;
import com.tongji.cst3492.jentor.model.WorkspaceOutline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditTargetResolverTest {
    private final WorkspaceIndex index = new WorkspaceIndex(
            "D:/demo",
            1L,
            List.of(
                    new WorkspaceOutline(
                            "src/main/java/com/example/OrderService.java",
                            "com.example",
                            "OrderService",
                            80,
                            List.of("void cancelOrder()", "int paymentFee()")),
                    new WorkspaceOutline(
                            "src/main/java/com/example/OrderDemo.java",
                            "com.example",
                            "OrderDemo",
                            30,
                            List.of("void main()"))));

    @Test
    void locatesTheFileMentionedByTheUser() {
        assertEquals("src/main/java/com/example/OrderService.java",
                EditTargetResolver.pick(
                        index,
                        "src/main/java/com/example/OrderDemo.java",
                        "修复 OrderService.cancelOrder",
                        ""));
    }

    @Test
    void usesTheAssistantSuggestionWhenTheUserSaysFixIt() {
        assertEquals("src/main/java/com/example/OrderService.java",
                EditTargetResolver.pick(
                        index,
                        "src/main/java/com/example/OrderDemo.java",
                        "修吧",
                        "OrderService.cancelOrder 缺少空值检查"));
    }

    @Test
    void fallsBackToTheCurrentEditorForAnUnrelatedSignal() {
        assertEquals("src/main/java/com/example/OrderDemo.java",
                EditTargetResolver.pick(
                        index,
                        "src/main/java/com/example/OrderDemo.java",
                        "修复这个文件里的问题",
                        ""));
    }
}
