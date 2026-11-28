package com.afterme.backend.handoverdocument.dto;

import java.time.Instant;

public record PublicationResponse(Long documentId, Long publishedVersionId, Instant publishedAt,
                                  long resourceVersion) {}
