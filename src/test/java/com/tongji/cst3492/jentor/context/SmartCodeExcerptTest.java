package com.tongji.cst3492.jentor.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartCodeExcerptTest {
    @Test
    void keepsTheRelevantWholeMethodAndTheFileTail() {
        StringBuilder source = new StringBuilder("public class OrderService {\n");
        for (int line = 0; line < 180; line++) {
            source.append("    int filler").append(line).append(" = ").append(line).append(";\n");
        }
        source.append("    public void cancelOrder(Order order) {\n")
                .append("        order.markCancelled();\n")
                .append("        paymentService.refund(order);\n")
                .append("    }\n");
        for (int line = 0; line < 100; line++) {
            source.append("    int later").append(line).append(" = ").append(line).append(";\n");
        }
        source.append("    public void finalizeOrder() {\n")
                .append("        auditLog.write(\"finished\");\n")
                .append("    }\n")
                .append("}\n");

        String excerpt = SmartCodeExcerpt.render(
                source.toString(), "检查 cancelOrder 空指针", 3_000);

        assertTrue(excerpt.contains("public void cancelOrder(Order order)"));
        assertTrue(excerpt.contains("paymentService.refund(order);"));
        assertTrue(excerpt.contains("public void finalizeOrder()"));
        assertTrue(excerpt.contains("[omitted lines "));
        assertFalse(excerpt.startsWith("    int filler0"));
    }

    @Test
    void keepsASmallFileWhole() {
        String source = "class Small {\n    void run() { }\n}\n";

        assertTrue(SmartCodeExcerpt.render(source, "run", 1_000).equals(source));
    }
}
