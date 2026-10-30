package com.tongji.cst3492.ta.llm;

import com.tongji.cst3492.ta.context.CodeFilePolicy;
import com.tongji.cst3492.ta.model.CodeContext;
import com.tongji.cst3492.ta.model.WorkspaceContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderWorkspaceTest {
    private static final WorkspaceContext OUTLINE = new WorkspaceContext(
            "PROJECT OUTLINE (2 source files, structure only - no method bodies):\n"
                    + "--- src/main/java/demo/A.java -- class A ---\n"
                    + "    public void run()\n", false, 2);

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void outlineComesAfterTheSelectionAndTheFiles() {
        CodeContext context = selection()
                .withFiles(List.of(CodeFilePolicy.snapshot(
                        "src/main/java/demo/A.java", "JAVA", "class A {}\n")))
                .withWorkspace(OUTLINE);

        String prompt = builder.buildIdeContext(context);

        int selection = prompt.indexOf("--- selected code ---");
        int files = prompt.indexOf("WORKSPACE FILES");
        int outline = prompt.indexOf("PROJECT OUTLINE");
        assertTrue(selection >= 0, "selection missing");
        assertTrue(files > selection, "files must follow the selection");
        assertTrue(outline > files, "the outline must be the last section");
        assertTrue(prompt.contains("--- end of project outline ---"));
    }

    @Test
    void aStaleOutlineIsMarkedInThePrompt() {
        WorkspaceContext stale = new WorkspaceContext(OUTLINE.text(), true, OUTLINE.fileCount());

        String prompt = builder.buildIdeContext(CodeContext.empty().withWorkspace(stale));

        assertTrue(prompt.contains("PROJECT OUTLINE"));
        assertTrue(prompt.contains("may be outdated"), prompt);
        assertFalse(prompt.contains("(none)"));
    }

    @Test
    void anEmptyWorkspaceContextKeepsTheContextEmpty() {
        assertEquals("IDE CONTEXT:\n(none)\n",
                builder.buildIdeContext(CodeContext.empty().withWorkspace(WorkspaceContext.empty())));
    }

    private static CodeContext selection() {
        return new CodeContext("Demo.java", "JAVA", 10, 12, "void run()", "class Demo",
                "int before;", "run();", "int after;");
    }
}
