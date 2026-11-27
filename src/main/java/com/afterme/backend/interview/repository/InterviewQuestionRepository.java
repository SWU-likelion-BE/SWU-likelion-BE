package com.afterme.backend.interview.repository;

import com.afterme.backend.interview.domain.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {
    List<InterviewQuestion> findAllBySessionIdOrderBySequenceAsc(Long sessionId);

    Optional<InterviewQuestion> findByIdAndSessionId(Long id, Long sessionId);
}