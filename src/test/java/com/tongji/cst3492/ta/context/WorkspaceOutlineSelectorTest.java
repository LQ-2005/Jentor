package com.tongji.cst3492.ta.context;

import com.tongji.cst3492.ta.model.WorkspaceIndex;
import com.tongji.cst3492.ta.model.WorkspaceOutline;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceOutlineSelectorTest {
    private static final WorkspaceIndex INDEX = new WorkspaceIndex("/project", 0L, List.of(
            new WorkspaceOutline("src/main/java/demo/OrderService.java", "demo",
                    "class OrderService", 120,
                    List.of("public Order placeOrder(Customer customer)",
                            "public void cancel(long id)")),
            new WorkspaceOutline("src/main/java/demo/ArrayDemo.java", "demo",
                    "class ArrayDemo", 40, List.of("static void main(String[] args)"))));

    @Test
    void matchesOnTypeName() {
        String text = WorkspaceOutlineSelector.select(INDEX, "OrderService 是做什么的？");

        assertTrue(text.contains("PROJECT OUTLINE (2 source files"));
        assertTrue(text.contains("src/main/java/demo/OrderService.java"));
        assertFalse(text.contains("src/main/java/demo/ArrayDemo.java"));
    }

    @Test
    void matchesOnMemberName() {
        String text = WorkspaceOutlineSelector.select(INDEX, "placeOrder 在哪里实现的？");

        assertTrue(text.contains("src/main/java/demo/OrderService.java"));
        assertTrue(text.contains("placeOrder"));
    }

    @Test
    void fallsBackToRecentlyModifiedFilesWhenNothingMatches() {
        String text = WorkspaceOutlineSelector.select(INDEX, "今天上海天气怎么样");

        assertTrue(text.contains("no file or member name matched"), text);
        assertTrue(text.contains("src/main/java/demo/OrderService.java"));
        assertTrue(text.contains("src/main/java/demo/ArrayDemo.java"));
    }

    @Test
    void rankPrefersTheMostRecentlyModifiedFileOnFallback() {
        WorkspaceIndex index = new WorkspaceIndex("/project", 0L, List.of(
                new WorkspaceOutline("src/Old.java", "demo", "class Old", 10, List.of(), 100L),
                new WorkspaceOutline("src/New.java", "demo", "class New", 10, List.of(), 900L)));

        WorkspaceOutlineSelector.Ranking ranking =
                WorkspaceOutlineSelector.rank(index, "天气怎么样", 1);

        assertFalse(ranking.matched());
        assertEquals("src/New.java", ranking.files().get(0).path());
    }

    @Test
    void rankIsEmptyWithoutAnIndex() {
        assertTrue(WorkspaceOutlineSelector.rank(null, "anything", 3).files().isEmpty());
        assertTrue(WorkspaceOutlineSelector.rank(INDEX, "anything", 0).files().isEmpty());
    }

    @Test
    void respectsTheCharacterBudget() {
        List<WorkspaceOutline> files = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            List<String> members = new ArrayList<>();
            for (int member = 0; member < 20; member++) {
                members.add("public void order" + member + "(String value)");
            }
            files.add(new WorkspaceOutline("src/Order" + index + ".java", "demo",
                    "class Order" + index, 100, members));
        }
        WorkspaceIndex index = new WorkspaceIndex("/project", 0L, files);

        String text = WorkspaceOutlineSelector.select(index, "order");

        assertTrue(text.length() <= WorkspaceOutlineSelector.MAX_CHARS,
                () -> "selected " + text.length() + " chars");
        assertTrue(text.contains("PROJECT OUTLINE (40 source files"));
    }
}
