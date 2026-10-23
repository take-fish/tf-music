package com.fishmatrix.music.model;

import java.time.Instant;

public record Playlist(long id, String name, String description, int trackCount, Instant updatedAt) {
}
