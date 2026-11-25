package com.afterme.backend.qa.domain;

import com.afterme.backend.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class QaThreadTest {
    private static final Instant NOW = Instant.parse("2026-11-25T00:00:00Z");

    @Test void editingPreservesResolvedStatusAndOriginalReplyContext() {
        QaThread thread = QaThread.create(1L, 2L, 3L, 4L, " Original question ", NOW);
        QaReply reply = QaReply.create(thread, 5L, " Answer ", NOW.plusSeconds(1));
        thread.answered(NOW.plusSeconds(1));
        assertThat(thread.getStatus()).isEqualTo(QaStatus.RESOLVED);
        thread.edit("Changed question", NOW.plusSeconds(2));
        assertThat(thread.getStatus()).isEqualTo(QaStatus.RESOLVED);
        assertThat(thread.getRevision()).isEqualTo(2);
        assertThat(thread.getReplyCount()).isEqualTo(1);
        assertThat(reply.getQuestionRevision()).isEqualTo(1);
        assertThat(reply.getQuestionContent()).isEqualTo("Original question");
        assertThat(reply.getContent()).isEqualTo("Answer");
    }

    @Test void unchangedQuestionKeepsRevisionAndTimestamp() {
        QaThread thread = QaThread.create(1L, 2L, 3L, 4L, "Question", NOW);
        thread.answered(NOW.plusSeconds(1));
        thread.edit(" Question ", NOW.plusSeconds(2));
        assertThat(thread.getStatus()).isEqualTo(QaStatus.RESOLVED);
        assertThat(thread.getRevision()).isEqualTo(1);
        assertThat(thread.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test void blankAndOversizedContentIsRejected() {
        for (String invalid : new String[] {null, " \n\t", "a".repeat(10001)}) {
            assertThatThrownBy(() -> QaThread.create(1L, 2L, 3L, 4L, invalid, NOW)).isInstanceOf(BusinessException.class);
        }
    }
}
