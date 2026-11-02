package com.tongji.cst3492.jentor.llm;

import com.tongji.cst3492.jentor.model.Chunk;
import com.tongji.cst3492.jentor.model.ContextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitationValidatorTest {
    private static final String PAGE_26 = "[Lec-2-OOP-with-Java.pdf, p.26]";
    private static final String PAGE_28 = "[Lec-2-OOP-with-Java.pdf, p.28]";
    private final CitationValidator validator = new CitationValidator();
    private final List<ContextBlock> blocks = List.of(block(26), block(28));

    @Test
    void exampleCodeAndProseCannotAccidentallyStartTheGeneralSection() {
        String answer = """
                # Java Interface Example
                ## Course basis
                An interface is a contract. %s
                ## Example
                ```java
                String label = "General supplement";
                interface Relatable { int compareTo(Relatable other); }
                ```
                The course's example interface is Relatable. %s
                """.formatted(PAGE_26, PAGE_28);

        assertTrue(validator.invalidCitations(answer, blocks).isEmpty());
        assertEquals(2, validator.citedSources(answer, blocks).size());
        assertFalse(validator.hasCitationInGeneralSection(answer));
    }

    @Test
    void mentioningTheSectionNameInASentenceDoesNotChangeSections() {
        String answer = "The General supplement section is optional.\n"
                + "## Course basis\nAn interface is a contract. " + PAGE_26;

        assertTrue(validator.invalidCitations(answer, blocks).isEmpty());
        assertEquals(1, validator.citedSources(answer, blocks).size());
    }

    @Test
    void aGeneralSectionEndsAtTheNextHeadingOfTheSameLevel() {
        String answer = "## General supplement\nAn original example, without a course citation.\n"
                + "### Implementation details\nMore original explanation.\n"
                + "## Course basis\nInterfaces can be implemented by classes. " + PAGE_28;

        assertTrue(validator.invalidCitations(answer, blocks).isEmpty());
        assertEquals(1, validator.citedSources(answer, blocks).size());
        assertTrue(validator.hasCourseSection(answer));
    }

    @Test
    void citationsInAGeneralSubsectionAreStillRejectedAndExcludedFromSources() {
        String answer = "## Course basis\nAn interface is a contract. " + PAGE_26
                + "\n## General supplement\n### Example explanation\n"
                + "This invented example came from the course. " + PAGE_28;

        assertEquals(List.of("CITATION_IN_GENERAL_SECTION"),
                validator.invalidCitations(answer, blocks));
        assertEquals(List.of(26), validator.citedSources(answer, blocks).stream()
                .map(source -> source.pageStart()).toList());
    }

    @Test
    void recognizesPlainBoldAndBilingualSectionLabels() {
        for (String heading : List.of("General supplement", "**General supplement**",
                "## General supplement (通用补充)", "## 通用补充（General supplement）")) {
            String answer = "Course basis\n" + PAGE_26 + "\n\n" + heading + "\n" + PAGE_28;
            assertTrue(validator.hasCitationInGeneralSection(answer), heading);
            assertEquals(1, validator.citedSources(answer, blocks).size(), heading);
        }
    }

    @Test
    void citationShapedStringsInCodeAreNotAnswerReferences() {
        String answer = """
                ## Course basis
                ```java
                // General supplement
                String citation = "[Lec-2-OOP-with-Java.pdf, p.99]";
                ```
                Inline sample: `[Other.pdf, p.5]`.
                """;

        assertTrue(validator.parse(answer).isEmpty());
        assertEquals(List.of("NO_CITATIONS"), validator.invalidCitations(answer, blocks));
        assertTrue(validator.citedSources(answer, blocks).isEmpty());
    }

    @Test
    void stillRejectsUnsupportedCoursePages() {
        String citation = "[Lec-2-OOP-with-Java.pdf, p.99]";
        assertEquals(List.of(citation), validator.invalidCitations(
                "## Course basis\nAn interface claim. " + citation, blocks));
    }

    @Test
    void supportsNonPdfLocatorTypes() {
        String slideCitation = "[Lecture.pptx, slide 4]";
        String sectionCitation = "[Notes.docx, section 3]";
        Chunk slide = new Chunk("slide-4", "slides", "Lecture", "Lecture.pptx",
                "SLIDE", 4, 4, 0, "Security", "Filters protect requests.", new float[512]);
        Chunk section = new Chunk("section-3", "notes", "Notes", "Notes.docx",
                "SECTION", 3, 3, 0, "Exceptions", "Checked exceptions.", new float[512]);

        List<ContextBlock> mixed = List.of(
                new ContextBlock(slide, slide.getText(), false),
                new ContextBlock(section, section.getText(), false));

        assertTrue(validator.invalidCitations(
                "## Course basis\nSlides. " + slideCitation + "\nSections. " + sectionCitation,
                mixed).isEmpty());
        assertEquals(2, validator.citedSources(
                "## Course basis\nSlides. " + slideCitation + "\nSections. " + sectionCitation,
                mixed).size());
    }

    private static ContextBlock block(int page) {
        Chunk chunk = new Chunk("interface-" + page, "oop", "Lec-2-OOP-with-Java",
                "Lec-2-OOP-with-Java.pdf", "PAGE", page, page, 0,
                "Interfaces", "An interface is a contract implemented by classes.", new float[512]);
        return new ContextBlock(chunk, chunk.getText(), false);
    }
}
