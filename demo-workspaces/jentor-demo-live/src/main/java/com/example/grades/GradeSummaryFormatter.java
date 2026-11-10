package com.example.grades;

public final class GradeSummaryFormatter {
    public String format(Student student, int index) {
        if (student == null) {
            return index + ". <missing>";
        }
        return index + ". " + student.name() + " | " + student.score();
    }
}
