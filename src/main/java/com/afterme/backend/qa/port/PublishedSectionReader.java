package com.afterme.backend.qa.port;

/** Document module adapter must verify project ownership and published-version visibility. */
public interface PublishedSectionReader {
    SectionReference requirePublishedSection(Long projectId, Long sectionId);

    record SectionReference(Long projectId, Long versionId, Long sectionId) {}
}
