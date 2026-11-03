package com.tongji.cst3492.jentor.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
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
        if (project == null || file == null || !"java".equalsIgnoreCase(file.getExtension())) {
            return "";
        }
        try {
            PsiFile proposed = PsiFileFactory.getInstance(project)
                    .createFileFromText(file.getName(), file.getFileType(), proposedText);
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
}
