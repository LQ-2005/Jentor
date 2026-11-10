package com.example.grades;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class GradeDemo {
    private GradeDemo() {
    }

    public static void main(String[] args) {
        List<Student> students = Arrays.asList(
                new Student("Alice", 92.0),
                new Student("Bob", 71.0),
                null,
                new Student("Cara", 64.0)
        );

        GradeService gradeService = new GradeService(70.0);
        GradeSummaryFormatter formatter = new GradeSummaryFormatter();
        Student topStudent = gradeService.topStudent(students);

        System.out.printf(Locale.ROOT, "Students: %d%n", students.size());
        System.out.printf(Locale.ROOT, "Average score: %.1f%n", gradeService.averageScore(students));
        System.out.printf(
                Locale.ROOT,
                "Top student: %s%n",
                topStudent == null ? "<none>" : topStudent.name()
        );

        for (int index = 0; index < students.size(); index++) {
            System.out.println(formatter.format(students.get(index), index + 1));
        }
    }
}
