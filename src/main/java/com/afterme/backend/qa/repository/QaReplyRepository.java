package com.afterme.backend.qa.repository;

import com.afterme.backend.qa.domain.QaReply;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QaReplyRepository extends JpaRepository<QaReply, Long> {
    List<QaReply> findByThread_IdOrderByIdAsc(Long threadId);
}
