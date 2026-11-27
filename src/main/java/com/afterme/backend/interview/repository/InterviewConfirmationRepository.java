package com.afterme.backend.interview.repository;

import com.afterme.backend.interview.domain.InterviewConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewConfirmationRepository extends JpaRepository<InterviewConfirmation, Long> {
    Optional<InterviewConfirmation> findFirstBySessionIdOrderByIdDesc(Long sessionId);
}