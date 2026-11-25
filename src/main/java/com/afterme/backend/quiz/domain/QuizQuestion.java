package com.afterme.backend.quiz.domain;

import jakarta.persistence.*;
import lombok.Getter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quiz_question")
@Getter
public class QuizQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_set_id")
    private QuizSet quizSet;
    @Column(nullable = false) private int sequence;
    @Column(nullable = false, columnDefinition = "text") private String stem;
    @ElementCollection
    @CollectionTable(name = "quiz_option", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "option_index")
    @Column(name = "content", nullable = false, columnDefinition = "text")
    private List<String> options = new ArrayList<>();
    @Column(nullable = false) private int correctOption;
    @Column(nullable = false, columnDefinition = "text") private String explanation;
    @Column(nullable = false) private Long sectionId;
    @Column(nullable = false) private String topic;

    protected QuizQuestion() {}

    public static QuizQuestion multipleChoice(String stem, List<String> options, int correctOption,
                                               String explanation, Long sectionId, String topic) {
        if (stem == null || stem.isBlank() || options == null || options.size() < 2
                || options.stream().anyMatch(s -> s == null || s.isBlank())
                || correctOption < 0 || correctOption >= options.size()
                || explanation == null || explanation.isBlank() || sectionId == null
                || topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Invalid multiple choice question");
        }
        QuizQuestion question = new QuizQuestion();
        question.stem = stem;
        question.options = new ArrayList<>(options);
        question.correctOption = correctOption;
        question.explanation = explanation;
        question.sectionId = sectionId;
        question.topic = topic;
        return question;
    }

    void attach(QuizSet set, int sequence) {
        if (quizSet != null) throw new IllegalArgumentException("Question already belongs to a quiz");
        this.quizSet = set;
        this.sequence = sequence;
    }

    public List<String> getOptions() { return List.copyOf(options); }
}
