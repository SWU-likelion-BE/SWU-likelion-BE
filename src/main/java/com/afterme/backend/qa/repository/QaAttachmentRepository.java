package com.afterme.backend.qa.repository;

import com.afterme.backend.qa.domain.QaAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QaAttachmentRepository extends JpaRepository<QaAttachment, Long> {
    @Query("""
            select a.id as id, a.originalFilename as originalFilename, a.contentType as contentType,
                   a.sizeBytes as sizeBytes
            from QaAttachment a
            where a.threadId = :threadId
            order by a.id
            """)
    List<Metadata> listMetadata(@Param("threadId") Long threadId);

    Optional<QaAttachment> findByIdAndThreadId(Long id, Long threadId);

    @Modifying
    @Query("delete from QaAttachment a where a.id = :id and a.threadId = :threadId")
    int deleteFromThread(@Param("id") Long id, @Param("threadId") Long threadId);

    interface Metadata {
        Long getId();
        String getOriginalFilename();
        String getContentType();
        long getSizeBytes();
    }
}
