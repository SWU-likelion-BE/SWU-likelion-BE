package com.afterme.backend.completion.repository;

import com.afterme.backend.completion.domain.CompletionCriteria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompletionCriteriaRepository extends JpaRepository<CompletionCriteria, Long> {}
