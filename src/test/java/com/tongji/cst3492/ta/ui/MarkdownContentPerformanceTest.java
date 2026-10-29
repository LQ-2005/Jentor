package com.tongji.cst3492.ta.ui;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the cost of re-rendering a streamed answer.
 *
 * <p>Since answers stream in, {@code ChatPanel} re-renders the whole message on
 * every flush (80 ms), which means the full Markdown is parsed again over
 * growing text. Parsing is the expensive stage of that re-render and the only
 * one that can run without a live IDE, so these tests pin its cost rather than
 * the Swing tree built on top of it.</p>
 *
 * <p>The assertions are relative on purpose: they catch an algorithmic change
 * (a second parse per flush, a normalisation that becomes quadratic, a regex
 * that backtracks on long input) and stay independent of how fast the machine
 * is. The budgets sit roughly three times above the measured cost, so ordinary
 * JIT and scheduler noise does not fail the build.</p>
 */
class MarkdownContentPerformanceTest {
    /** Matches one flush worth of added text, as sent by the streaming client. */
    private static final int STREAM_STEP = 200;
    private static final int STREAM_INPUT = 24_000;
    private static final int SMALL_INPUT = 8_000;
    private static final int LARGE_INPUT = 64_000;

    @Test
    void parseCostPerCharacterDoesNotGrowWithTheAnswer() {
        String small = answer(SMALL_INPUT);
        String large = answer(LARGE_INPUT);
        parse(small);
        parse(large);

        long smallNanos = bestOf(5, () -> parse(small));
        long largeNanos = bestOf(5, () -> parse(large));
        double smallPerChar = smallNanos / (double) small.length();
        double largePerChar = largeNanos / (double) large.length();

        // Linear parsing keeps the per-character cost flat. A parser that scans
        // the already-parsed prefix again would make the 8x larger answer cost
        // 8x more per character, which this bound rejects.
        assertTrue(largePerChar <= smallPerChar * 4.0, () -> String.format(Locale.ROOT,
                "Parsing cost per character grew with the answer: %.2f us/char at %d chars "
                        + "-> %.2f us/char at %d chars (%.1fx). This points at super-linear "
                        + "work in MarkdownContent.parse, which the streaming re-render cannot "
                        + "afford.",
                smallPerChar / 1000.0, small.length(),
                largePerChar / 1000.0, large.length(),
                largePerChar / smallPerChar));
    }

    @Test
    void streamedReRenderingStaysProportionalToASingleParse() {
        String answer = answer(STREAM_INPUT);
        parse(answer);
        streamParse(answer);
        parse(answer);

        long singleNanos = bestOf(7, () -> parse(answer));
        long streamedNanos = bestOf(3, () -> streamParse(answer));
        int flushes = answer.length() / STREAM_STEP;

        // Each flush re-parses its prefix, so the streamed total is inherently
        // larger than one full parse: sum(prefix) / length. Anything close to an
        // extra factor on top of that (an extra parse per flush, a per-flush
        // rebuild of already rendered blocks) pushes the ratio far higher.
        double ratio = (double) streamedNanos / Math.max(1, singleNanos);
        assertTrue(ratio <= 60.0, () -> String.format(Locale.ROOT,
                "Re-parsing %d chars over %d flushes cost %.1fx a single parse "
                        + "(%d us vs %d us). MarkdownContent.parse should cost roughly one "
                        + "parse per flush; a much larger ratio means the work done for each "
                        + "flush grows with the answer.",
                answer.length(), flushes, ratio, streamedNanos / 1000, singleNanos / 1000));
    }

    private static int parse(String text) {
        return MarkdownContent.parse(text).size();
    }

    private static void streamParse(String text) {
        for (int end = STREAM_STEP; end <= text.length(); end += STREAM_STEP) {
            MarkdownContent.parse(text.substring(0, end));
        }
    }

    private static long bestOf(int rounds, Runnable task) {
        long best = Long.MAX_VALUE;
        for (int round = 0; round < rounds; round++) {
            long started = System.nanoTime();
            task.run();
            best = Math.min(best, System.nanoTime() - started);
        }
        return best;
    }

    /** A deterministic answer with headings, lists, inline code and code fences. */
    private static String answer(int approximateChars) {
        StringBuilder builder = new StringBuilder();
        int section = 0;
        while (builder.length() < approximateChars) {
            section++;
            builder.append("## Section ").append(section).append("\n\n");
            builder.append("A paragraph explaining a Java concept, with `inline code` and a ")
                    .append("**strong** claim about generics. Type erasure removes parameter ")
                    .append("types at compile time, so runtime type information is limited to ")
                    .append("the raw type.\n\n");
            builder.append("- first point about interfaces\n")
                    .append("- second point about abstract classes\n")
                    .append("- third point about constructors\n\n");
            builder.append("```java\n")
                    .append("public class Example").append(section).append(" {\n")
                    .append("    private final List<String> values = new ArrayList<>();\n")
                    .append("    public void add(String value) { values.add(value); }\n")
                    .append("}\n```\n\n");
        }
        return builder.toString();
    }
}
