package com.fishmatrix.music.model;

public record TrackUpdateRequest(
        String title,
        String artist,
        String album,
        Integer durationSec,
        Long categoryId,
        String status) {
}
