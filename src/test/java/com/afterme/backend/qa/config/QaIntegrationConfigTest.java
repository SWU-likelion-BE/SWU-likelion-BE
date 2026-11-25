package com.afterme.backend.qa.config;

import com.afterme.backend.global.exception.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class QaIntegrationConfigTest {
    @Test void absentDocumentIntegrationFailsClosed() {
        var reader = new QaIntegrationConfig().unavailablePublishedSectionReader();
        assertThatThrownBy(() -> reader.requirePublishedSection(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
    }
}
