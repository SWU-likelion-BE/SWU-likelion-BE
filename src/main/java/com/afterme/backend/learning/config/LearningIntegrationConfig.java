package com.afterme.backend.learning.config;

import com.afterme.backend.completion.port.CompletenessPolicy;
import com.afterme.backend.global.exception.*;
import com.afterme.backend.learning.port.LearningDocumentReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import java.util.*;

@Configuration
public class LearningIntegrationConfig {
    @Bean @ConditionalOnMissingBean(LearningDocumentReader.class)
    LearningDocumentReader unavailableLearningDocuments() {
        return new LearningDocumentReader() {
            public PublishedVersion requirePublishedVersion(Long projectId, Long versionId) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            public ChecklistItem requirePublishedChecklistItem(Long projectId, Long documentId, Long versionId, Long itemId) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            public Evidence evidence(Long projectId, Set<Long> successors) { return Evidence.unavailable(); }
        };
    }

    @Bean @ConditionalOnMissingBean(CompletenessPolicy.class)
    CompletenessPolicy unconfiguredCompletenessPolicy() { return evidence -> OptionalInt.empty(); }
}
