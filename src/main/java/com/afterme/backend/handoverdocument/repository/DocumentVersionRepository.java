package com.afterme.backend.handoverdocument.repository;

import com.afterme.backend.handoverdocument.domain.DocumentVersion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    Optional<DocumentVersion> findByGenerationJobId(String generationJobId);

    @EntityGraph(attributePaths = {"document", "sections"})
    Optional<DocumentVersion> findByIdAndDocument_TaskId(Long id, Long taskId);

    @EntityGraph(attributePaths = {"document", "sections"})
    Optional<DocumentVersion> findByIdAndDocument_Id(Long id, Long documentId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into handover_document_version
                (document_id, confirmation_id, generation_job_id, status, title, revision, created_at, updated_at)
            values (:documentId, :confirmationId, :jobId, 'DRAFT', :title, 0, :now, :now)
            on conflict (generation_job_id) do nothing
            """, nativeQuery = true)
    int insertDraft(@Param("documentId") Long documentId, @Param("confirmationId") Long confirmationId,
                    @Param("jobId") String jobId, @Param("title") String title, @Param("now") Instant now);
}
