package com.afterme.backend.completion.port;

import com.afterme.backend.completion.service.ReadinessService.ScoringEvidence;
import java.util.OptionalInt;

/** Server-side scoring only. Empty means no agreed scoring formula; never substitute a made-up score. */
public interface CompletenessPolicy {
    OptionalInt score(ScoringEvidence evidence);
}
