package com.afterme.backend.interview.repository;

import com.afterme.backend.interview.domain.AnswerRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnswerRevisionRepository extends JpaRepository<AnswerRevision, Long> {
    List<AnswerRevision> findAllByAnswerIdOrderByRevisionNoDesc(Long answerId);
}