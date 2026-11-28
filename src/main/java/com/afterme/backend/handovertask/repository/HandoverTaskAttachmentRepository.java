package com.afterme.backend.handovertask.repository;

import com.afterme.backend.handovertask.domain.HandoverTaskAttachment;
import com.afterme.backend.handovertask.dto.HandoverTaskAttachmentResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HandoverTaskAttachmentRepository extends JpaRepository<HandoverTaskAttachment, Long> {
    @Query("""
            select new com.afterme.backend.handovertask.dto.HandoverTaskAttachmentResponse(
                a.id, a.originalFilename, a.contentType, a.sizeBytes, a.createdAt)
            from HandoverTaskAttachment a
            where a.taskId = :taskId
            order by a.id
            """)
    List<HandoverTaskAttachmentResponse> listMetadata(Long taskId);

    Optional<HandoverTaskAttachment> findByIdAndTaskId(Long id, Long taskId);

    @Modifying
    @Query("delete from HandoverTaskAttachment a where a.id = :id and a.taskId = :taskId")
    int deleteFromTask(@Param("id") Long id, @Param("taskId") Long taskId);
}
