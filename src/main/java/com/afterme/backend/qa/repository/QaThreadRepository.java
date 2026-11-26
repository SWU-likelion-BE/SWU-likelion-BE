package com.afterme.backend.qa.repository;

import com.afterme.backend.qa.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface QaThreadRepository extends JpaRepository<QaThread, Long> {
    @Query("select t from QaThread t where t.projectId = :projectId and (:status is null or t.status = :status)")
    Page<QaThread> findPage(@Param("projectId") Long projectId, @Param("status") QaStatus status, Pageable pageable);
    Optional<QaThread> findByIdAndProjectId(Long id, Long projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from QaThread t where t.id = :id and t.projectId = :projectId")
    Optional<QaThread> findForUpdate(@Param("id") Long id, @Param("projectId") Long projectId);
    long countByProjectIdAndStatus(Long projectId, QaStatus status);
    long countByProjectId(Long projectId);
    long countByProjectIdAndAuthorId(Long projectId, Long authorId);
    long countByProjectIdAndAuthorIdAndStatus(Long projectId, Long authorId, QaStatus status);
}
