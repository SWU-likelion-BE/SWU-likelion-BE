package com.afterme.backend.qa.config;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.qa.port.PublishedSectionReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QaIntegrationConfig {
    @Bean
    @ConditionalOnMissingBean(PublishedSectionReader.class)
    PublishedSectionReader unavailablePublishedSectionReader() {
        // Fail closed until the document module supplies a verified section reader.
        return (projectId, sectionId) -> { throw new BusinessException(ErrorCode.CONFLICT); };
    }
}
