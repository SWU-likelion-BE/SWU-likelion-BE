package com.afterme.backend.checklist.domain;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;

@Entity
@Table(name = "checklist_completion", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "item_id", "user_id"}))
@Getter
public class ChecklistCompletion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false) private Long documentId;
    @Column(nullable = false) private Long versionId;
    @Column(nullable = false) private Long itemId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private boolean completed;
    @Column(nullable = false) private Instant updatedAt;
    protected ChecklistCompletion() {}

    public static ChecklistCompletion create(Long projectId, Long documentId, Long versionId, Long itemId,
                                              Long userId, boolean completed, Instant now) {
        var result = new ChecklistCompletion();
        result.projectId = projectId; result.documentId = documentId; result.versionId = versionId;
        result.itemId = itemId; result.userId = userId; result.completed = completed; result.updatedAt = now;
        return result;
    }

    public void change(boolean completed, Instant now) {
        if (this.completed != completed) { this.completed = completed; this.updatedAt = now; }
    }
}
