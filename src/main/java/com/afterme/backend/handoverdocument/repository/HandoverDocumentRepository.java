package com.afterme.backend.handoverdocument.repository;

import com.afterme.backend.handoverdocument.domain.HandoverDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HandoverDocumentRepository extends JpaRepository<HandoverDocument, Long> {
    Optional<HandoverDocument> findByTaskId(Long taskId);
}
