package com.tongji.cst3492.jentor.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.cst3492.jentor.model.EditPlan;
import com.tongji.cst3492.jentor.model.EditPlanEntry;
import com.tongji.cst3492.jentor.model.SkippedEditFile;

import java.util.ArrayList;
import java.util.List;

/** Parses the planner's JSON into a bounded multi-file edit plan. */
public final class EditPlanParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EditPlanParser() {
    }

    public static EditPlan parse(String answer) {
        if (answer == null || answer.isBlank()) {
            return new EditPlan("", List.of(), List.of(),
                    "The edit planner returned no plan.");
        }
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return new EditPlan("", List.of(), List.of(),
                    "The edit planner returned an invalid plan.");
        }
        try {
            JsonNode root = MAPPER.readTree(answer.substring(start, end + 1));
            List<EditPlanEntry> files = new ArrayList<>();
            JsonNode filesNode = root.path("files");
            if (filesNode.isArray()) {
                for (JsonNode node : filesNode) {
                    String path = node.path("path").asText("");
                    String instruction = node.path("instruction").asText("");
                    if (!path.isBlank() && !instruction.isBlank()
                            && files.size() < EditPlan.MAX_FILES) {
                        files.add(new EditPlanEntry(path, instruction));
                    }
                }
            }
            List<SkippedEditFile> skipped = new ArrayList<>();
            JsonNode skippedNode = root.path("skipped");
            if (skippedNode.isArray()) {
                for (JsonNode node : skippedNode) {
                    String path = node.path("path").asText("");
                    if (!path.isBlank()) {
                        skipped.add(new SkippedEditFile(path, node.path("reason").asText("")));
                    }
                }
            }
            return new EditPlan(
                    root.path("summary").asText(""),
                    files,
                    skipped,
                    root.path("clarification").asText(""));
        } catch (Exception error) {
            return new EditPlan("", List.of(), List.of(),
                    "The edit planner returned an invalid plan.");
        }
    }
}
