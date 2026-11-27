package com.afterme.backend.handovertask.repository;

import com.afterme.backend.handovertask.domain.HandoverTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HandoverTaskRepository extends JpaRepository<HandoverTask, Long> {

    List<HandoverTask> findAllByProjectIdOrderByIdAsc(Long projectId);

    Optional<HandoverTask> findByIdAndProjectId(Long taskId, Long projectId);
}
