package com.fishmatrix.music.model;

public record RecordRequest(
        String trackId,
        String userId,
        String action,
        double positionSec) {
}
