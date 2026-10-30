package com.tongji.cst3492.ta.context;

import com.tongji.cst3492.ta.model.WorkspaceOutline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutlineExtractorTest {
    private static final String SOURCE = """
            package com.example.demo;

            import java.util.List;

            /** A service. */
            public class OrderService extends BaseService implements Auditable {
                private final List<String> values = new ArrayList<>();

                @Override
                public Order placeOrder(Customer customer) throws ValidationException {
                    if (customer == null) {
                        throw new IllegalArgumentException();
                    }
                    return null;
                }

                public void cancel(long id) { }
            }
            """;

    @Test
    void extractsPackageTypeAndMethodSignatures() {
        WorkspaceOutline outline = OutlineExtractor.extract("src/OrderService.java", SOURCE);

        assertEquals("com.example.demo", outline.packageName());
        assertTrue(outline.typeName().startsWith("class OrderService"), outline.typeName());
        assertTrue(outline.typeName().contains("extends BaseService"), outline.typeName());
        assertTrue(outline.members().stream().anyMatch(m -> m.contains("placeOrder")),
                outline.members().toString());
        assertTrue(outline.members().stream().anyMatch(m -> m.contains("cancel")),
                outline.members().toString());
    }

    @Test
    void ignoresFieldsControlFlowAndComments() {
        WorkspaceOutline outline = OutlineExtractor.extract("src/OrderService.java", SOURCE);

        assertTrue(outline.members().stream().noneMatch(m -> m.startsWith("if ")));
        assertTrue(outline.members().stream().noneMatch(m -> m.contains("values")));
        assertTrue(outline.members().stream().noneMatch(m -> m.trim().startsWith("*")));
    }

    @Test
    void capsTheMemberList() {
        StringBuilder source = new StringBuilder("package demo;\npublic class Big {\n");
        for (int index = 0; index < 80; index++) {
            source.append("    public void method").append(index).append("() { }\n");
        }
        source.append("}\n");

        WorkspaceOutline outline = OutlineExtractor.extract("src/Big.java", source.toString());

        assertEquals(OutlineExtractor.MAX_MEMBERS, outline.members().size());
    }

    @Test
    void extractsInterfaceMethodsThatEndWithSemicolons() {
        String source = "package demo;\n\npublic interface Repo {\n"
                + "    List<String> findAll();\n"
                + "    void save(String value);\n"
                + "}\n";

        WorkspaceOutline outline = OutlineExtractor.extract("src/Repo.java", source);

        assertTrue(outline.typeName().startsWith("interface Repo"), outline.typeName());
        assertTrue(outline.members().stream().anyMatch(m -> m.contains("findAll")),
                outline.members().toString());
        assertTrue(outline.members().stream().anyMatch(m -> m.contains("save")));
    }

    @Test
    void skipsAnnotationImportAndCommentLines() {
        String source = "package demo;\n"
                + "import java.util.List;\n"
                + "@Deprecated\n"
                + "public class A {\n"
                + "    // a comment\n"
                + "    @Override\n"
                + "    public String toString() { return \"a\"; }\n"
                + "}\n";

        WorkspaceOutline outline = OutlineExtractor.extract("src/A.java", source);

        assertTrue(outline.members().stream().noneMatch(m -> m.startsWith("@")));
        assertTrue(outline.members().stream().noneMatch(m -> m.startsWith("import")));
        assertTrue(outline.members().stream().noneMatch(m -> m.startsWith("//")));
        assertTrue(outline.members().stream().anyMatch(m -> m.contains("toString")),
                outline.members().toString());
    }

    @Test
    void countsLinesWithoutTheTrailingNewline() {
        WorkspaceOutline outline = OutlineExtractor.extract("src/Tiny.java",
                "package demo;\n\npublic class Tiny {\n}\n");

        assertEquals(4, outline.lineCount());
    }
}
