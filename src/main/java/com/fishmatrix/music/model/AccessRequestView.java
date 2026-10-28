package com.fishmatrix.music.model;

import com.fishmatrix.music.domain.AccessRequestStatus;
import java.time.Instant;

public record AccessRequestView(
        long id,
        long trackId,
        String trackTitle,
        long ownerId,
        String ownerNickname,
        long requesterId,
        String requesterNickname,
        AccessRequestStatus status,
        String message,
        String decisionMessage,
        Instant createdAt,
        Instant decidedAt) {
}
