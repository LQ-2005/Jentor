package com.example.grades;

import java.util.List;

public final class GradeService {
    private final double passingScore;

    public GradeService(double passingScore) {
        this.passingScore = passingScore;
    }

    public double averageScore(List<Student> students) {
        if (students == null || students.isEmpty()) {
            return 0.0;
        }

        int count = 0;
        double total = 0.0;
        for (Student student : students) {
            if (student != null) {
                total += student.score();
                count++;
            }
        }
        return count == 0 ? 0.0 : total / count;
    }

    public Student topStudent(List<Student> students) {
        if (students == null || students.isEmpty()) {
            return null;
        }

        Student top = null;
        for (Student student : students) {
            if (student != null && (top == null || student.score() > top.score())) {
                top = student;
            }
        }
        return top;
    }
}
