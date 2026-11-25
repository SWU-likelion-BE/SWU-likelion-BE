package com.afterme.backend.quiz.repository;

import com.afterme.backend.quiz.domain.QuizSet;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface QuizSetRepository extends JpaRepository<QuizSet, Long> {
    Page<QuizSet> findByProjectId(Long projectId, Pageable pageable);
    Optional<QuizSet> findByIdAndProjectId(Long id, Long projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QuizSet q where q.id = :id and q.projectId = :projectId")
    Optional<QuizSet> findForUpdate(Long id, Long projectId);
}
