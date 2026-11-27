package com.afterme.backend.interview.repository;

import com.afterme.backend.interview.domain.InterviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, Long> {
    Optional<InterviewAnswer> findByQuestionId(Long questionId);

    List<InterviewAnswer> findAllByQuestionIdIn(Collection<Long> questionIds);
}