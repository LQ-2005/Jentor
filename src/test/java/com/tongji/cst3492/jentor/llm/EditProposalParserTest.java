package com.tongji.cst3492.jentor.llm;

import com.tongji.cst3492.jentor.model.EditProposal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditProposalParserTest {
    private static final String ANSWER = """
            SUMMARY: refactor to strategy
            ```jentor-edit path=src/main/java/demo/A.java
            class A {
                void run() { }
            }
            ```
            """;

    @Test
    void parsesThePathAndTheCompleteFile() {
        EditProposalParser.Result result =
                EditProposalParser.parse(ANSWER, 42L, "refactor to strategy");

        assertTrue(result.hasProposal());
        EditProposal proposal = result.proposal();
        assertEquals("src/main/java/demo/A.java", proposal.path());
        assertEquals(42L, proposal.baseModificationStamp());
        assertEquals("refactor to strategy", proposal.instruction());
        assertTrue(proposal.newText().startsWith("class A {"), proposal.newText());
        assertTrue(proposal.newText().endsWith("}"), proposal.newText());
        assertFalse(proposal.newText().endsWith("\n"));
    }

    @Test
    void normalizesWindowsPaths() {
        String answer = "```jentor-edit path=\"src\\main\\java\\demo\\A.java\"\n"
                + "class A {}\n```";

        EditProposal proposal = EditProposalParser.parse(answer, 0L, "").proposal();

        assertEquals("src/main/java/demo/A.java", proposal.path());
    }

    @Test
    void recognizesNoChange() {
        EditProposalParser.Result result =
                EditProposalParser.parse("The file is fine.\nNO_CHANGE", 1L, "fix");

        assertTrue(result.noChange());
        assertFalse(result.hasProposal());
        assertNull(result.refusal());
    }

    @Test
    void recognizesRefusal() {
        EditProposalParser.Result result =
                EditProposalParser.parse("REFUSE: the change would break the public API", 1L, "rename");

        assertFalse(result.hasProposal());
        assertFalse(result.noChange());
        assertEquals("the change would break the public API", result.refusal());
    }

    @Test
    void rejectsMissingOrEmptyBlocks() {
        assertFalse(EditProposalParser.parse("just prose", 1L, "").hasProposal());
        assertFalse(EditProposalParser.parse("", 1L, "").hasProposal());
        assertFalse(EditProposalParser.parse(
                "```jentor-edit path=src/A.java\n   \n```",
                1L, "").hasProposal());
    }
}
