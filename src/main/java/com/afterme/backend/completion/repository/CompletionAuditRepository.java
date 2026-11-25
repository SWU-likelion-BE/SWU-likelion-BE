package com.afterme.backend.completion.repository;
import com.afterme.backend.completion.domain.CompletionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CompletionAuditRepository extends JpaRepository<CompletionAudit, Long> {}
