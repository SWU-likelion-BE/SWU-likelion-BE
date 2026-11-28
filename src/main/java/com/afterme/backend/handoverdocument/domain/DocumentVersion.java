package com.afterme.backend.handoverdocument.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "handover_document_version")
@Getter
public class DocumentVersion {
    public enum Status { DRAFT, PUBLISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", updatable = false)
    private HandoverDocument document;

    @Column(nullable = false, updatable = false)
    private Long confirmationId;

    @Column(nullable = false, unique = true, updatable = false)
    private String generationJobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private long revision;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant publishedAt;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<DocumentSection> sections = new ArrayList<>();

    protected DocumentVersion() {
    }

    public List<DocumentSection> getSections() {
        return List.copyOf(sections);
    }

    public void addSection(String type, String title, String content, int position) {
        sections.add(new DocumentSection(this, type, title, content, position));
    }

    public void removeSections(Collection<DocumentSection> removed) {
        sections.removeAll(removed);
    }

    public void publish(Instant now) {
        status = Status.PUBLISHED;
        publishedAt = now;
        updatedAt = now;
        revision++;
    }

    public void recordEdit(String title, Instant now) {
        this.title = title;
        this.revision++;
        this.updatedAt = now;
    }
}
