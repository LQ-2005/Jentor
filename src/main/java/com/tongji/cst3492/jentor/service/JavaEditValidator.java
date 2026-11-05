package com.tongji.cst3492.jentor.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Collection;

/** Validates a proposed Java file before it can be applied. */
public final class JavaEditValidator {
    private JavaEditValidator() {
    }

    public static String validationError(
            Project project, VirtualFile file, String proposedText) {
        if (project == null || file == null) {
            return "";
        }
        return validationError(project, file.getName(), proposedText);
    }

    public static String validationError(
            Project project, String fileName, String proposedText) {
        if (project == null || fileName == null
                || !"java".equalsIgnoreCase(extension(fileName))) {
            return "";
        }
        try {
            PsiFile proposed = PsiFileFactory.getInstance(project)
                    .createFileFromText(
                            fileName,
                            FileTypeManager.getInstance().getFileTypeByFileName(fileName),
                            proposedText);
            Collection<PsiErrorElement> errors =
                    PsiTreeUtil.collectElementsOfType(proposed, PsiErrorElement.class);
            if (errors.isEmpty()) {
                return "";
            }
            PsiErrorElement first = errors.iterator().next();
            return first.getErrorDescription();
        } catch (RuntimeException error) {
            return "Java syntax validation failed: " + error.getMessage();
        }
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1);
    }
}
