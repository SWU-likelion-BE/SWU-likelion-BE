package com.afterme.backend.handoverdocument.service;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.handoverdocument.domain.DocumentSection;
import com.afterme.backend.handoverdocument.domain.DocumentVersion;
import com.afterme.backend.handoverdocument.domain.HandoverDocument;
import com.afterme.backend.handoverdocument.dto.DraftResponse;
import com.afterme.backend.handoverdocument.dto.DraftSectionInput;
import com.afterme.backend.handoverdocument.dto.UpdateDraftRequest;
import com.afterme.backend.handoverdocument.repository.DocumentVersionRepository;
import com.afterme.backend.handoverdocument.repository.HandoverDocumentRepository;
import com.afterme.backend.handovertask.domain.HandoverTask;
import com.afterme.backend.handovertask.repository.HandoverTaskRepository;
import com.afterme.backend.interview.repository.InterviewConfirmationRepository;
import com.afterme.backend.interview.repository.InterviewSessionRepository;
import com.afterme.backend.learning.service.LearningAccess;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.afterme.backend.global.exception.ErrorCode.*;
import static com.afterme.backend.handover.domain.ParticipantRole.PREDECESSOR;

@Service
@RequiredArgsConstructor
public class HandoverDocumentService {
    private final HandoverDocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final HandoverTaskRepository tasks;
    private final InterviewConfirmationRepository confirmations;
    private final InterviewSessionRepository sessions;
    private final LearningAccess access;
    private final Validator validator;
    private final Clock clock;

    @Transactional
    public Long createDraftFromGeneration(Long taskId, Long confirmationId, List<DraftSectionInput> sections,
                                          String generationJobId) {
        if (taskId == null || confirmationId == null || generationJobId == null
                || generationJobId.isBlank() || generationJobId.length() > 255) {
            throw error(INVALID_INPUT);
        }

        HandoverTask task = tasks.findById(taskId).orElseThrow(() -> error(RESOURCE_NOT_FOUND));
        var project = access.lock(task.getProjectId());
        var existing = versions.findByGenerationJobId(generationJobId);
        if (existing.isPresent()) {
            return requireSameGeneration(existing.get(), taskId, confirmationId);
        }

        access.mutable(project);
        confirmations.findById(confirmationId)
                .flatMap(confirmation -> sessions.findById(confirmation.getSessionId()))
                .filter(session -> taskId.equals(session.getTaskId()))
                .orElseThrow(() -> error(RESOURCE_NOT_FOUND));
        if (sections == null || sections.isEmpty() || sections.stream()
                .anyMatch(section -> section == null || !validator.validate(section).isEmpty())) {
            throw error(INVALID_INPUT);
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        HandoverDocument document = documents.findByTaskId(taskId)
                .orElseGet(() -> documents.save(new HandoverDocument(taskId, now)));
        int inserted = versions.insertDraft(document.getId(), confirmationId, generationJobId, task.getTitle(), now);
        DocumentVersion version = versions.findByGenerationJobId(generationJobId).orElseThrow();
        if (inserted == 0) {
            return requireSameGeneration(version, taskId, confirmationId);
        }

        for (int position = 0; position < sections.size(); position++) {
            DraftSectionInput section = sections.get(position);
            version.addSection(section.type(), section.title(), section.content(), position);
        }
        return version.getId();
    }

    @Transactional(readOnly = true)
    public DraftResponse getDraft(Long userId, Long projectId, Long taskId, Long versionId) {
        access.role(userId, projectId, PREDECESSOR);
        return toResponse(requireDraft(projectId, taskId, versionId));
    }

    @Transactional
    public DraftResponse updateDraft(Long userId, Long projectId, Long taskId, Long versionId,
                                     UpdateDraftRequest request) {
        access.role(userId, projectId, PREDECESSOR);
        access.mutable(access.lock(projectId));

        DocumentVersion version = requireDraft(projectId, taskId, versionId);
        if (version.getRevision() != request.revision()) {
            throw error(CONFLICT);
        }

        Map<Long, DocumentSection> existing = new HashMap<>();
        version.getSections().forEach(section -> existing.put(section.getId(), section));
        Set<Long> seen = new HashSet<>();

        for (int position = 0; position < request.sections().size(); position++) {
            UpdateDraftRequest.Section section = request.sections().get(position);
            if (section.sectionId() == null) {
                version.addSection(section.type(), section.title(), section.content(), position);
                continue;
            }

            if (!seen.add(section.sectionId())) {
                throw error(INVALID_INPUT);
            }
            DocumentSection current = existing.remove(section.sectionId());
            if (current == null) {
                throw error(RESOURCE_NOT_FOUND);
            }
            current.update(section.type(), section.title(), section.content(), position);
        }

        // Entries left in the map were omitted by the replacement request.
        version.removeSections(existing.values());
        version.recordEdit(request.title(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        versions.flush();
        return toResponse(version);
    }

    private DocumentVersion requireDraft(Long projectId, Long taskId, Long versionId) {
        tasks.findByIdAndProjectId(taskId, projectId).orElseThrow(() -> error(RESOURCE_NOT_FOUND));
        DocumentVersion version = versions.findByIdAndDocument_TaskId(versionId, taskId)
                .orElseThrow(() -> error(RESOURCE_NOT_FOUND));
        if (version.getStatus() != DocumentVersion.Status.DRAFT) {
            throw error(CONFLICT);
        }
        return version;
    }

    private Long requireSameGeneration(DocumentVersion version, Long taskId, Long confirmationId) {
        if (!taskId.equals(version.getDocument().getTaskId()) || !confirmationId.equals(version.getConfirmationId())) {
            throw error(CONFLICT);
        }
        return version.getId();
    }

    private DraftResponse toResponse(DocumentVersion version) {
        List<DraftResponse.Section> sections = version.getSections().stream()
                .sorted(Comparator.comparingInt(DocumentSection::getPosition))
                .map(section -> new DraftResponse.Section(section.getId(), section.getType(),
                        section.getTitle(), section.getContent(), section.getPosition()))
                .toList();
        return new DraftResponse(version.getDocument().getId(), version.getId(), version.getDocument().getTaskId(),
                version.getConfirmationId(), version.getTitle(), version.getRevision(), version.getUpdatedAt(), sections);
    }

    private BusinessException error(ErrorCode code) {
        return new BusinessException(code);
    }
}
