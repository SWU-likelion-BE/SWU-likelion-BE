package com.afterme.backend.interview.repository;

import com.afterme.backend.interview.domain.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    Optional<InterviewSession> findByTaskId(Long taskId);

    Optional<InterviewSession> findByIdAndProjectId(Long id, Long projectId);
}