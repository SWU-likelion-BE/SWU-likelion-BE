package com.afterme.backend.quiz.repository;

import com.afterme.backend.quiz.domain.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.Collection;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByQuizSet_ProjectIdAndUserIdInAndSubmittedAtIsNotNull(Long projectId, Collection<Long> userIds);
    Optional<QuizAttempt> findFirstByQuizSet_IdAndUserIdOrderByAttemptNoDesc(Long quizSetId, Long userId);
    Optional<QuizAttempt> findByIdAndQuizSet_IdAndUserId(Long id, Long quizSetId, Long userId);
}
