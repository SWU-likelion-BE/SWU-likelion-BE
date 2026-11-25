package com.afterme.backend.quiz.domain;

import jakarta.persistence.*;
import lombok.Getter;
import java.util.ArrayList;
import java.util.List;

/** Immutable quiz snapshot. Only the generation integration may create a ready set. */
@Entity
@Table(name = "quiz_set")
@Getter
public class QuizSet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false) private Long versionId;
    @Column(nullable = false) private int passingScore;
    @OneToMany(mappedBy = "quizSet", cascade = CascadeType.ALL)
    @OrderBy("sequence ASC")
    private List<QuizQuestion> questions = new ArrayList<>();

    protected QuizSet() {}

    public static QuizSet ready(Long projectId, Long publishedVersionId, int passingScore,
                                List<QuizQuestion> questions) {
        if (projectId == null || publishedVersionId == null || passingScore < 70 || passingScore > 100
                || questions == null || questions.isEmpty() || questions.size() > 100
                || questions.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Invalid quiz snapshot");
        }
        QuizSet set = new QuizSet();
        set.projectId = projectId;
        set.versionId = publishedVersionId;
        set.passingScore = passingScore;
        for (int i = 0; i < questions.size(); i++) {
            QuizQuestion question = questions.get(i);
            question.attach(set, i + 1);
            set.questions.add(question);
        }
        return set;
    }

    public List<QuizQuestion> getQuestions() { return List.copyOf(questions); }
}
