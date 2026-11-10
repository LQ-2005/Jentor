package com.tongji.cst3492.jentor.llm;

import com.tongji.cst3492.jentor.model.EditProposal;
import com.tongji.cst3492.jentor.model.EditOperation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the edit-mode reply. The model must return one fenced block, or the
 * single word {@code NO_CHANGE}, or a {@code REFUSE: reason} line.
 */
public final class EditProposalParser {
    private static final Pattern BLOCK = Pattern.compile(
            "```jentor-edit\\s+path=\"?([^\\s\"`]+)\"?\\s*\\R([\\s\\S]*?)```",
            Pattern.MULTILINE);
    private static final Pattern REFUSE =
            Pattern.compile("^\\s*REFUSE\\s*:?\\s*(.*)$", Pattern.MULTILINE);
    private static final Pattern NO_CHANGE =
            Pattern.compile("^\\s*NO_CHANGE\\s*$", Pattern.MULTILINE);

    private EditProposalParser() {
    }

    /** Either a proposal, a refusal reason, or the "nothing to change" marker. */
    public record Result(EditProposal proposal, String refusal, boolean noChange) {
        public boolean hasProposal() {
            return proposal != null;
        }
    }

    public static Result parse(String answer, long baseModificationStamp, String instruction) {
        return parse(answer, baseModificationStamp, instruction, "");
    }

    public static Result parse(
            String answer,
            long baseModificationStamp,
            String instruction,
            String baseContentHash) {
        if (answer == null || answer.isBlank()) {
            return new Result(null, "the model returned no content", false);
        }
        Matcher block = BLOCK.matcher(answer);
        if (block.find()) {
            String path = block.group(1).replace('\\', '/').strip();
            String content = stripFenceNewline(block.group(2));
            if (path.isBlank()) {
                return new Result(null, "the edit block has no path", false);
            }
            if (content.isBlank()) {
                return new Result(null, "the proposed file is empty", false);
            }
            return new Result(
                    new EditProposal(
                            path,
                            baseModificationStamp,
                            content,
                            instruction,
                            EditOperation.MODIFY,
                            baseContentHash),
                    null,
                    false);
        }
        if (NO_CHANGE.matcher(answer).find()) {
            return new Result(null, null, true);
        }
        Matcher refuse = REFUSE.matcher(answer);
        if (refuse.find()) {
            String reason = refuse.group(1).strip();
            return new Result(null, reason.isEmpty() ? "the model refused the request" : reason, false);
        }
        return new Result(null, "the model did not return a jentor-edit block", false);
    }

    /** The fence adds one newline; the file content keeps its own. */
    private static String stripFenceNewline(String content) {
        if (content.endsWith("\r\n")) {
            return content.substring(0, content.length() - 2);
        }
        if (content.endsWith("\n")) {
            return content.substring(0, content.length() - 1);
        }
        return content;
    }
}
