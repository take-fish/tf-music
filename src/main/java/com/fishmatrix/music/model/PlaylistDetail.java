package com.fishmatrix.music.model;

import java.util.List;

public record PlaylistDetail(Playlist playlist, List<TrackSummary> tracks) {
}
