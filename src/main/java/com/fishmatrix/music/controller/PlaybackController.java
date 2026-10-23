package com.fishmatrix.music.controller;

import com.fishmatrix.common.auth.UserContext;
import com.fishmatrix.music.model.PlaybackRecord;
import com.fishmatrix.music.model.RecordRequest;
import com.fishmatrix.music.service.PlaybackService;
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
public class PlaybackController {

    private final PlaybackService playbackService;

    public PlaybackController(PlaybackService playbackService) {
        this.playbackService = playbackService;
    }

    @PostMapping("/tracks/{trackId}/records")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> record(@PathVariable long trackId,
            @RequestBody RecordRequest request) {
        PlaybackRecord record = playbackService.record(
                trackId, request, UserContext.require());
        return Map.of("recordId", record.id(), "ts", record.ts());
    }

    @GetMapping("/records")
    public Map<String, Object> records(@RequestParam(defaultValue = "20") int limit) {
        return Map.of("records",
                playbackService.records(UserContext.require(), limit));
    }
}
