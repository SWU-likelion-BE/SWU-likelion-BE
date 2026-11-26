package com.afterme.backend.learning.port;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;

/** Implemented by the document module; every returned reference must be published and project-scoped. */
public interface LearningDocumentReader {
    PublishedVersion requirePublishedVersion(Long projectId, Long versionId);
    ChecklistItem requirePublishedChecklistItem(Long projectId, Long documentId, Long versionId, Long itemId);
    Evidence evidence(Long projectId, Set<Long> successorIds);

    /** Complete scoring population, including optional items; empty Optional means not connected. */
    default Optional<List<ChecklistItem>> scoringChecklistItems(Long projectId, Set<Long> versionIds) {
        return Optional.empty();
    }

    /** All pages in every requested published version must have been opened by each returned user.
     * Acknowledgement alone is not page-view evidence. An unconnected adapter must fail closed. */
    default PageViews pageViews(Long projectId, Set<Long> versionIds, Set<Long> successorIds) {
        return PageViews.unavailable();
    }

    record PageCount(int totalPages, int viewedPages) {
        public PageCount {
            if (totalPages <= 0 || viewedPages < 0 || viewedPages > totalPages)
                throw new IllegalArgumentException("Invalid page counts");
        }
    }
    record PageViews(boolean available, Set<Long> fullyViewedSuccessorIds, Map<Long, PageCount> progress) {
        public PageViews {
            fullyViewedSuccessorIds = Set.copyOf(fullyViewedSuccessorIds);
            progress = Map.copyOf(progress);
            for (var entry : progress.entrySet()) {
                var count = entry.getValue();
                if (fullyViewedSuccessorIds.contains(entry.getKey()) != (count.totalPages() == count.viewedPages()))
                    throw new IllegalArgumentException("Page counts contradict completion state");
            }
        }
        /** Legacy adapters can validate completion but cannot supply a partial-view score. */
        public PageViews(boolean available, Set<Long> fullyViewedSuccessorIds) {
            this(available, fullyViewedSuccessorIds, Map.of());
        }
        public static PageViews fromProgress(Map<Long, PageCount> progress) {
            Set<Long> complete = progress.entrySet().stream()
                    .filter(e -> e.getValue().totalPages() == e.getValue().viewedPages())
                    .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
            return new PageViews(true, complete, progress);
        }
        public static PageViews unavailable() { return new PageViews(false, Set.of(), Map.of()); }
    }

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
