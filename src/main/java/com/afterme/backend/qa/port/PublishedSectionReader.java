package com.afterme.backend.qa.port;

import java.util.List;

/** Document module adapter must verify project ownership and published-version visibility. */
public interface PublishedSectionReader {
    SectionReference requirePublishedSection(Long projectId, Long sectionId);

    default List<SectionReference> listPublishedSections(Long projectId) {
        return List.of();
    }

    record SectionReference(Long projectId, Long versionId, Long sectionId, Long taskId,
                            String title, Integer displayOrder) {
        public SectionReference(Long projectId, Long versionId, Long sectionId) {
            this(projectId, versionId, sectionId, null, null, null);
        }
    }
}
