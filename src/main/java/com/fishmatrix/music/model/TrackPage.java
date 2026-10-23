package com.fishmatrix.music.model;

import java.util.List;

public record TrackPage(List<TrackSummary> tracks, long total, int page, int size) {
}
