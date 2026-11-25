package com.afterme.backend.quiz.repository;

import com.afterme.backend.quiz.domain.QuizGenerationJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface QuizGenerationJobRepository extends JpaRepository<QuizGenerationJob, Long> {
    Optional<QuizGenerationJob> findByProjectIdAndRequestKey(Long projectId, String requestKey);
    Optional<QuizGenerationJob> findByIdAndProjectId(Long id, Long projectId);
    Page<QuizGenerationJob> findByStatus(QuizGenerationJob.Status status, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from QuizGenerationJob j where j.id = :id")
    Optional<QuizGenerationJob> findForUpdate(@Param("id") Long id);
}
