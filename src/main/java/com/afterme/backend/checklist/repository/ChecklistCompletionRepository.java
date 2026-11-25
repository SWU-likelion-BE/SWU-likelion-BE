package com.afterme.backend.checklist.repository;

import com.afterme.backend.checklist.domain.ChecklistCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ChecklistCompletionRepository extends JpaRepository<ChecklistCompletion, Long> {
    Optional<ChecklistCompletion> findByProjectIdAndItemIdAndUserId(Long projectId, Long itemId, Long userId);
    List<ChecklistCompletion> findByProjectIdAndCompletedTrue(Long projectId);
}
