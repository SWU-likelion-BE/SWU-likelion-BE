package com.afterme.backend.quiz.domain;

import com.afterme.backend.learning.port.LearningDocumentReader.Section;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "quiz_generation_job", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "request_key"}))
@Getter
public class QuizGenerationJob {
    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false) private Long documentId;
    @Column(nullable = false) private Long versionId;
    @Column(nullable = false) private Long requestedBy;
    @Column(nullable = false, length = 100) private String requestKey;
    @Column(nullable = false) private int passingScore;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private int attemptCount;
    @Column(nullable = false) private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Long quizSetId;
    @Column(length = 100) private String errorCode;
    private String provider;
    private String model;
    private String promptVersion;
    @ElementCollection
    @CollectionTable(name = "quiz_generation_source", joinColumns = @JoinColumn(name = "job_id"))
    @OrderColumn(name = "source_position")
    private List<Source> sources = new ArrayList<>();

    @Embeddable @Getter
    public static class Source {
        @Column(nullable = false) private Long sectionId;
        @Column(nullable = false) private String type;
        @Column(nullable = false, columnDefinition = "text") private String content;
        protected Source() {}
        Source(Section section) { sectionId = section.sectionId(); type = section.type(); content = section.content(); }
    }

    protected QuizGenerationJob() {}
    public static QuizGenerationJob queue(Long projectId, Long documentId, Long versionId, Long userId,
                                          String key, int passingScore, List<Section> sections, Instant now) {
        var job = new QuizGenerationJob();
        job.projectId = projectId; job.documentId = documentId; job.versionId = versionId; job.requestedBy = userId;
        job.requestKey = key; job.passingScore = passingScore; job.status = Status.QUEUED; job.createdAt = now;
        job.sources = sections.stream().map(Source::new).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return job;
    }
    public void start(Instant now) { status = Status.RUNNING; startedAt = now; attemptCount++; }
    public void succeed(Long quizSetId, String provider, String model, String promptVersion, Instant now) {
        this.status = Status.SUCCEEDED; this.quizSetId = quizSetId; this.provider = provider; this.model = model;
        this.promptVersion = promptVersion; this.finishedAt = now; this.errorCode = null;
    }
    public void fail(String code, Instant now) { status = Status.FAILED; errorCode = code; finishedAt = now; }
    public List<Source> getSources() { return List.copyOf(sources); }
}
