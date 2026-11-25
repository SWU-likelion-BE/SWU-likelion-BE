package com.afterme.backend.learning.port;

import java.util.List;
import java.util.Set;

/** Implemented by the document module; every returned reference must be published and project-scoped. */
public interface LearningDocumentReader {
    PublishedVersion requirePublishedVersion(Long projectId, Long versionId);
    ChecklistItem requirePublishedChecklistItem(Long projectId, Long documentId, Long versionId, Long itemId);
    Evidence evidence(Long projectId, Set<Long> successorIds);

    record Section(Long sectionId, String type, String content) {}
    record PublishedVersion(Long projectId, Long documentId, Long versionId, List<Section> sections) {
        public PublishedVersion { sections = List.copyOf(sections); }
    }
    record ChecklistItem(Long projectId, Long documentId, Long versionId, Long itemId) {}
    record Evidence(boolean available, int taskCount, int missingRequiredTasks, int requiredAnswers,
                    int answeredRequiredQuestions, Set<Long> requiredVersionIds,
                    Set<Long> acknowledgedSuccessorIds, List<ChecklistItem> requiredChecklistItems) {
        public Evidence {
            requiredVersionIds = Set.copyOf(requiredVersionIds);
            acknowledgedSuccessorIds = Set.copyOf(acknowledgedSuccessorIds);
            requiredChecklistItems = List.copyOf(requiredChecklistItems);
            if (taskCount < 0 || missingRequiredTasks < 0 || requiredAnswers < 0
                    || answeredRequiredQuestions < 0 || answeredRequiredQuestions > requiredAnswers) {
                throw new IllegalArgumentException("Invalid document evidence counts");
            }
        }
        public static Evidence unavailable() {
            return new Evidence(false, 0, 0, 0, 0, Set.of(), Set.of(), List.of());
        }
    }
}
