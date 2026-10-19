package com.fishmatrix.music.model;

import java.util.List;

public record Track(
        String id,
        String title,
        String artist,
        int durationSec,
        String stage,
        List<String> lyrics) {
}
