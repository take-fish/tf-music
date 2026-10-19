package com.fishmatrix.music.model;

import java.util.List;

public record PlaylistDetail(
        String id,
        String name,
        int buildNumber,
        List<Track> tracks) {
}
