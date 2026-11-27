package com.afterme.backend.interview.repository;

import com.afterme.backend.interview.domain.InterviewQuestionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewQuestionTemplateRepository extends JpaRepository<InterviewQuestionTemplate, Long> {
    List<InterviewQuestionTemplate> findAllByActiveTrueOrderBySequenceAsc();
}