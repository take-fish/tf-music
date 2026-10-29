package com.fishmatrix.music.controller;

import com.fishmatrix.common.auth.RequirePermission;
import com.fishmatrix.common.auth.UserContext;
import com.fishmatrix.music.model.Playlist;
import com.fishmatrix.music.model.PlaylistDetail;
import com.fishmatrix.music.model.PlaylistRequest;
import com.fishmatrix.music.service.PlaylistService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/music")
@RequirePermission("music.private.manage")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistService playlistService;

    @GetMapping("/playlists")
    public Map<String, Object> list() {
        List<Playlist> playlists = playlistService.list(UserContext.require());
        return Map.of("playlists", playlists);
    }

    @GetMapping("/playlists/{playlistId}")
    public PlaylistDetail get(@PathVariable long playlistId) {
        return playlistService.get(playlistId, UserContext.require());
    }

    @PostMapping("/playlists")
    @ResponseStatus(HttpStatus.CREATED)
    public Playlist create(@RequestBody PlaylistRequest request) {
        return playlistService.create(request, UserContext.require());
    }

    @PutMapping("/playlists/{playlistId}")
    public Playlist update(@PathVariable long playlistId,
            @RequestBody PlaylistRequest request) {
        return playlistService.update(playlistId, request, UserContext.require());
    }

    @DeleteMapping("/playlists/{playlistId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long playlistId) {
        playlistService.delete(playlistId, UserContext.require());
    }

    @PostMapping("/playlists/{playlistId}/tracks/{trackId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addTrack(@PathVariable long playlistId, @PathVariable long trackId) {
        playlistService.addTrack(playlistId, trackId, UserContext.require());
    }

    @DeleteMapping("/playlists/{playlistId}/tracks/{trackId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTrack(@PathVariable long playlistId, @PathVariable long trackId) {
        playlistService.removeTrack(playlistId, trackId, UserContext.require());
    }
}
