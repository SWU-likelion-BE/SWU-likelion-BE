package com.afterme.backend.handoverdocument.domain;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "handover_document_section")
@Getter
public class DocumentSection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", updatable = false)
    private DocumentVersion version;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private int position;

    protected DocumentSection() {
    }

    DocumentSection(DocumentVersion version, String type, String title, String content, int position) {
        this.version = version;
        update(type, title, content, position);
    }

    public void update(String type, String title, String content, int position) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.position = position;
    }
}
