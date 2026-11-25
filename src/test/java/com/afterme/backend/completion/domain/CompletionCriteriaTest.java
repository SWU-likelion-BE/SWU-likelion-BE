package com.afterme.backend.completion.domain;

import com.afterme.backend.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class CompletionCriteriaTest {
    static final Instant NOW = Instant.parse("2026-11-25T00:00:00Z");

    @Test void partialUpdatePreservesOtherFieldAndRecordsActor() {
        var criteria = CompletionCriteria.defaults(1L);
        criteria.update(null, 85, 2L, NOW);
        assertThat(criteria.getMinimumCompleteness()).isEqualTo(80);
        assertThat(criteria.getQuizPassingScore()).isEqualTo(85);
        assertThat(criteria.getRevision()).isEqualTo(1);
        assertThat(criteria.getUpdatedBy()).isEqualTo(2L);
        assertThat(criteria.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test void unchangedValuesDoNotIncrementRevision() {
        var criteria = CompletionCriteria.defaults(1L);
        criteria.update(100, 100, 2L, NOW);
        criteria.update(100, null, 3L, NOW.plusSeconds(1));
        assertThat(criteria.getRevision()).isEqualTo(1);
        assertThat(criteria.getUpdatedBy()).isEqualTo(2L);
        criteria.update(80, 70, 2L, NOW.plusSeconds(2));
        assertThat(criteria.getRevision()).isEqualTo(2);
    }

    @Test void invalidAndEmptyUpdatesAreRejectedWithoutMutation() {
        var criteria = CompletionCriteria.defaults(1L);
        for (Integer[] invalid : new Integer[][]{{79, 70}, {101, 70}, {80, 69}, {80, 101}, {null, null}}) {
            assertThatThrownBy(() -> criteria.update(invalid[0], invalid[1], 2L, NOW)).isInstanceOf(BusinessException.class);
            assertThat(criteria.getRevision()).isZero();
        }
    }
}
