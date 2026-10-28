package com.fishmatrix.music.service;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.music.model.NotFoundException;
import com.fishmatrix.music.model.Playlist;
import com.fishmatrix.music.model.PlaylistDetail;
import com.fishmatrix.music.model.PlaylistRequest;
import com.fishmatrix.music.model.TrackSummary;
import com.fishmatrix.music.repository.PlaylistRepository;
import com.fishmatrix.music.repository.TrackRepository;
import com.fishmatrix.music.repository.TrackRepository.TrackRecord;
import com.fishmatrix.music.util.Validation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final TrackRepository trackRepository;
    private final MusicLibraryService libraryService;

    public List<Playlist> list(CurrentUser viewer) {
        return playlistRepository.findByOwner(viewer.id());
    }

    public PlaylistDetail get(long playlistId, CurrentUser viewer) {
        Playlist playlist = requireOwned(playlistId, viewer);
        List<TrackSummary> tracks = new ArrayList<>();
        for (Long trackId : playlistRepository.findTrackIds(playlistId)) {
            trackRepository.findById(trackId)
                    .filter(track -> canRead(track, viewer))
                    .ifPresent(track -> tracks.add(libraryService.summary(track, viewer)));
        }
        return new PlaylistDetail(playlist, tracks);
    }

    @Transactional
    public Playlist create(PlaylistRequest request, CurrentUser viewer) {
        if (request == null) throw new IllegalArgumentException("请求体不能为空");
        String name = Validation.text(request.name(), 64, "歌单名称", true);
        String description = Validation.truncate(request.description(), 255);
        long id = playlistRepository.insert(viewer.id(), name, description, Instant.now());
        return playlistRepository.findById(id).orElseThrow();
    }

    @Transactional
    public Playlist update(long playlistId, PlaylistRequest request, CurrentUser viewer) {
        Playlist playlist = requireOwned(playlistId, viewer);
        if (request == null) throw new IllegalArgumentException("请求体不能为空");
        String name = request.name() == null
                ? playlist.name() : Validation.text(request.name(), 64, "歌单名称", true);
        String description = request.description() == null
                ? playlist.description() : Validation.truncate(request.description(), 255);
        playlistRepository.update(playlistId, name, description, Instant.now());
        return playlistRepository.findById(playlistId).orElseThrow();
    }

    @Transactional
    public void delete(long playlistId, CurrentUser viewer) {
        requireOwned(playlistId, viewer);
        playlistRepository.delete(playlistId);
    }

    @Transactional
    public void addTrack(long playlistId, long trackId, CurrentUser viewer) {
        requireOwned(playlistId, viewer);
        libraryService.requireReadable(trackId, viewer);
        playlistRepository.addTrack(playlistId, trackId, Instant.now());
    }

    @Transactional
    public void removeTrack(long playlistId, long trackId, CurrentUser viewer) {
        requireOwned(playlistId, viewer);
        playlistRepository.removeTrack(playlistId, trackId, Instant.now());
    }

    private Playlist requireOwned(long playlistId, CurrentUser viewer) {
        if (!playlistRepository.ownedBy(playlistId, viewer.id())) {
            throw new NotFoundException("歌单不存在");
        }
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new NotFoundException("歌单不存在"));
    }

    private boolean canRead(TrackRecord track, CurrentUser viewer) {
        if (track.isPublic()) {
            return track.isVisible() || viewer.hasPermission("music.content.manage.any");
        }
        return track.isOwnedBy(viewer.id())
                || trackRepository.hasApprovedAccess(track.id(), viewer.id());
    }
}
