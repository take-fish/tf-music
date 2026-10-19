package com.fishmatrix.music.model;

public record PlaylistSummary(
        String id,
        String name,
        String description,
        int buildNumber,
        int trackCount) {
}
