package com.fishmatrix.music.model;

public record PlaybackRecord(
        long id,
        String playlistId,
        String trackId,
        String userId,
        String action,
        double positionSec,
        long ts) {
}
