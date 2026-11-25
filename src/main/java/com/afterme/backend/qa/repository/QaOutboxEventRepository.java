package com.afterme.backend.qa.repository;

import com.afterme.backend.qa.domain.QaOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaOutboxEventRepository extends JpaRepository<QaOutboxEvent, Long> {}
