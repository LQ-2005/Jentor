package com.tongji.cst3492.jentor.llm;

import com.tongji.cst3492.jentor.context.CodeFilePolicy;
import com.tongji.cst3492.jentor.model.CodeContext;
import com.tongji.cst3492.jentor.model.CodeFileSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderFilesTest {
    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void emptyContextStillSaysNone() {
        assertEquals("IDE CONTEXT:\n(none)\n", builder.buildIdeContext(CodeContext.empty()));
        assertEquals("IDE CONTEXT:\n(none)\n", builder.buildIdeContext(null));
    }

    @Test
    void selectionComesFirstAndFilesAreTheLowestPrioritySection() {
        CodeContext context = selection().withFiles(List.of(
                CodeFilePolicy.snapshot("src/main/java/demo/App.java", "JAVA",
                        "class App {\n    void run() { }\n}\n")));

        String prompt = builder.buildIdeContext(context);

        int selectionSection = prompt.indexOf("--- selected code ---");
        int filesSection = prompt.indexOf("WORKSPACE FILES");
        assertTrue(selectionSection >= 0, "the selection section must be present");
        assertTrue(filesSection > selectionSection, "files must follow the selection");
        assertTrue(prompt.contains("lowest priority"));
        assertTrue(prompt.contains("src/main/java/demo/App.java (L1-L3)"));
        assertTrue(prompt.contains("class App {"));
        assertTrue(prompt.contains("--- end of workspace files ---"));
    }

    @Test
    void filesAloneStillProduceAContext() {
        CodeContext context = CodeContext.ofFiles(List.of(
                CodeFilePolicy.snapshot("src/demo/Only.java", "JAVA", "class Only {}\n")));

        String prompt = builder.buildIdeContext(context);

        assertTrue(prompt.contains("WORKSPACE FILES"));
        assertFalse(prompt.contains("(none)"));
    }

    @Test
    void theSectionStopsAtTheBudgetAndSaysSo() {
        List<CodeFileSnapshot> files = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            files.add(CodeFilePolicy.snapshot(
                    "src/demo/File" + index + ".java", "JAVA", bigBody()));
        }

        String section = builder.buildWorkspaceFiles(files);

        assertTrue(section.contains("omitted: context budget reached"));
        assertTrue(section.length() <= CodeFilePolicy.MAX_TOTAL_CHARS + 800,
                () -> "the section kept " + section.length() + " chars");
        assertTrue(section.contains("src/demo/File0.java"));
        assertFalse(section.contains("src/demo/File3.java"));
    }

    private static String bigBody() {
        StringBuilder builder = new StringBuilder();
        for (int line = 0; line < 400; line++) {
            builder.append("int value").append(line).append(" = ").append(line).append(";\n");
        }
        return builder.toString();
    }

    private static CodeContext selection() {
        return new CodeContext("Demo.java", "JAVA", 10, 12, "void run()", "class Demo",
                "int before;", "run();", "int after;");
    }
}
