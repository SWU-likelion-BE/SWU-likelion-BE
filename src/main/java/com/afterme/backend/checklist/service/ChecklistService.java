package com.afterme.backend.checklist.service;

import com.afterme.backend.checklist.domain.ChecklistCompletion;
import com.afterme.backend.checklist.repository.ChecklistCompletionRepository;
import com.afterme.backend.global.exception.*;
import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.learning.port.LearningDocumentReader;
import com.afterme.backend.learning.service.LearningAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChecklistService {
    private final LearningAccess access;
    private final LearningDocumentReader documents;
    private final ChecklistCompletionRepository completions;
    private final Clock clock;
    public record Result(Long itemId, Long userId, boolean completed, Instant updatedAt) {}

    @Transactional
    public Result change(Long userId, Long projectId, Long documentId, Long versionId, Long itemId, boolean completed) {
        access.role(userId, projectId, ParticipantRole.SUCCESSOR);
        access.mutable(access.lock(projectId));
        var reference = documents.requirePublishedChecklistItem(projectId, documentId, versionId, itemId);
        if (reference == null || !Objects.equals(reference.projectId(), projectId)
                || !Objects.equals(reference.documentId(), documentId) || !Objects.equals(reference.versionId(), versionId)
                || !Objects.equals(reference.itemId(), itemId)) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        var existing = completions.findByProjectIdAndItemIdAndUserId(projectId, itemId, userId);
        ChecklistCompletion result;
        if (existing.isPresent()) {
            result = existing.get();
            if (!Objects.equals(result.getDocumentId(), documentId) || !Objects.equals(result.getVersionId(), versionId))
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            result.change(completed, clock.instant());
        } else result = completions.save(ChecklistCompletion.create(projectId, documentId, versionId, itemId, userId, completed, clock.instant()));
        return new Result(result.getItemId(), result.getUserId(), result.isCompleted(), result.getUpdatedAt());
    }
}
