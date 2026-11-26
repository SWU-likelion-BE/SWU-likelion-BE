package com.afterme.backend.completion.port;

import com.afterme.backend.completion.service.ReadinessService.ScoringEvidence;
import java.util.OptionalInt;

/** Server-side scoring only. Empty means insufficient evidence or undefined project aggregation. */
public interface CompletenessPolicy {
    OptionalInt score(ScoringEvidence evidence);
}
