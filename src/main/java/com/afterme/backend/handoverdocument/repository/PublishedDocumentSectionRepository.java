package com.afterme.backend.handoverdocument.repository;

import com.afterme.backend.handoverdocument.domain.DocumentSection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PublishedDocumentSectionRepository extends Repository<DocumentSection, Long> {
    String PUBLISHED_SECTIONS = """
            select task.project_id as "projectId", version.id as "versionId", section.id as "sectionId",
                   task.id as "taskId", section.title as title, section.position + 1 as "displayOrder"
            from handover_document_section section
            join handover_document_version version on version.id = section.version_id
            join handover_document document on document.id = version.document_id
            join handover_task task on task.id = document.task_id
            where task.project_id = :projectId and version.status = 'PUBLISHED'
            """;

    @Query(value = PUBLISHED_SECTIONS + "and section.id = :sectionId", nativeQuery = true)
    Optional<PublishedSection> findPublished(@Param("projectId") Long projectId, @Param("sectionId") Long sectionId);

    @Query(value = PUBLISHED_SECTIONS + """
            and not exists (
                select 1 from handover_document_version newer
                where newer.document_id = document.id and newer.status = 'PUBLISHED'
                  and (coalesce(newer.published_at, newer.updated_at), newer.id)
                    > (coalesce(version.published_at, version.updated_at), version.id)
            )
            order by task.id, section.position
            """, nativeQuery = true)
    List<PublishedSection> listCurrentPublished(@Param("projectId") Long projectId);

    interface PublishedSection {
        Long getProjectId();
        Long getVersionId();
        Long getSectionId();
        Long getTaskId();
        String getTitle();
        Integer getDisplayOrder();
    }
}
