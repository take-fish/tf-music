package com.fishmatrix.music.model;

public record PlaybackRecord(
        long id,
        long trackId,
        String trackTitle,
        String action,
        int positionSec,
        long ts) {
}
