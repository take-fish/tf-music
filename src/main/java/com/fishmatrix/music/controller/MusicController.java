package com.fishmatrix.music.controller;

import com.fishmatrix.music.model.PlaybackRecord;
import com.fishmatrix.music.model.PlaylistDetail;
import com.fishmatrix.music.model.PlaylistSummary;
import com.fishmatrix.music.model.RecordRequest;
import com.fishmatrix.music.service.MusicCatalogService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/music")
public class MusicController {

    private final MusicCatalogService catalogService;

    public MusicController(MusicCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/playlists")
    public Map<String, Object> playlists() {
        List<PlaylistSummary> playlists = catalogService.playlists();
        return Map.of("playlists", playlists);
    }

    @GetMapping("/playlists/{playlistId}")
    public PlaylistDetail playlist(@PathVariable String playlistId) {
        return catalogService.playlist(playlistId);
    }

    @PostMapping("/playlists/{playlistId}/records")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> record(
            @PathVariable String playlistId,
            @RequestBody RecordRequest request) {
        PlaybackRecord record = catalogService.record(playlistId, request);
        return Map.of("recordId", record.id(), "ts", record.ts());
    }

    @GetMapping("/records")
    public Map<String, Object> records(@RequestParam(defaultValue = "20") int limit) {
        return Map.of("records", catalogService.records(limit));
    }
}
