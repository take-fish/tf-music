package com.fishmatrix.music.model;

import java.time.Instant;

public record AccessRequestView(
        long id,
        long trackId,
        String trackTitle,
        long ownerId,
        String ownerNickname,
        long requesterId,
        String requesterNickname,
        String status,
        String message,
        String decisionMessage,
        Instant createdAt,
        Instant decidedAt) {
}
