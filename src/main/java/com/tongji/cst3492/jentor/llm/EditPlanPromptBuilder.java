package com.tongji.cst3492.jentor.llm;

import com.tongji.cst3492.jentor.model.CodeFileSnapshot;
import com.tongji.cst3492.jentor.model.WorkspaceOutline;

import java.util.List;

/** Creates the read-only planning request that chooses files before editing. */
public final class EditPlanPromptBuilder {
    public List<ChatMessage> build(
            String question,
            String previousAnalysis,
            String activeFile,
            String activeFileContent,
            List<WorkspaceOutline> workspaceFiles,
            String language) {
        return build(question, previousAnalysis, activeFile, activeFileContent,
                workspaceFiles, List.of(), language);
    }

    public List<ChatMessage> build(
            String question,
            String previousAnalysis,
            String activeFile,
            String activeFileContent,
            List<WorkspaceOutline> workspaceFiles,
            List<CodeFileSnapshot> contextFiles,
            String language) {
        boolean zh = "zh".equals(language);
        String system = """
                You plan a code change for Jentor. Do not write code. Return JSON only.
                Select the smallest correct set of existing workspace source files.
                The plan may contain one or several files, but only files that need changes.
                Keep instructions concise: name the target and requested change without
                developing the implementation. A separate step will generate each file.

                Return:
                {
                  "summary": "short user-facing summary",
                  "files": [
                    {"path": "project-relative/path.java", "operation": "MODIFY|CREATE", "instruction": "exact changes for this file"}
                  ],
                  "skipped": [
                    {"path": "project-relative/path.java", "reason": "why it is not changed"}
                  ],
                  "clarification": ""
                }

                Rules:
                - MODIFY paths must come from AVAILABLE WORKSPACE FILES.
                - CREATE paths must be new project-relative source paths in an existing
                  package directory implied by those files.
                - Do not include read-only files in files; put them in skipped.
                - Do not invent files or paths.
                - Treat SOURCE SNAPSHOT as the current request-time code. It may contain
                  explicit [lines ...] and [omitted lines ...] markers. Do not assume an
                  omitted method has disappeared from the file.
                - If the target method is present, plan against that exact method rather
                  than asking the user to paste the file again.
                - Use CREATE only when a new source file is genuinely required.
                - CREATE paths must use an existing project package directory and must not exist.
                - If the latest request adopts a previous analysis, carry all relevant issues
                  into file-specific instructions.
                - Keep unrelated files out.
                - If the target or requested fixes are genuinely ambiguous, return no files
                  and put one concise question in clarification.
                - Never include code, patches or prose outside JSON.
                """;
        if (zh) {
            system += "\nWrite summary, reasons and clarification in Simplified Chinese.";
        } else {
            system += "\nWrite summary, reasons and clarification in English.";
        }

        StringBuilder user = new StringBuilder("ACTIVE FILE:\n")
                .append(activeFile == null || activeFile.isBlank() ? "(none)" : activeFile)
                .append("\n\nACTIVE FILE CONTENT:\n")
                .append(activeFileContent == null || activeFileContent.isBlank()
                        ? "(none)" : activeFileContent)
                .append("\n\nSOURCE SNAPSHOT:\n")
                .append(new PromptBuilder().buildWorkspaceFiles(
                        contextFiles == null ? List.of() : contextFiles,
                        PromptBuilder.MAX_IDE_CONTEXT_CHARS))
                .append("\n\nPREVIOUS ASSISTANT ANALYSIS:\n")
                .append(previousAnalysis == null || previousAnalysis.isBlank()
                        ? "(none)" : previousAnalysis)
                .append("\n\nAVAILABLE WORKSPACE FILES:\n");
        for (WorkspaceOutline file : workspaceFiles == null ? List.<WorkspaceOutline>of() : workspaceFiles) {
            user.append(file.path());
            if (!file.typeName().isBlank()) {
                user.append(" | ").append(file.typeName());
            }
            if (!file.members().isEmpty()) {
                user.append(" | ").append(String.join("; ", file.members()));
            }
            user.append('\n');
        }
        user.append("\nLATEST USER REQUEST:\n")
                .append(question == null ? "" : question.strip());
        return List.of(
                ChatMessage.system(system.strip()),
                ChatMessage.user(user.toString()));
    }
}
