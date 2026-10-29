package com.fishmatrix.music.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.music.converter.ViewConverters;
import com.fishmatrix.music.entity.MusicPlaylist;
import com.fishmatrix.music.entity.MusicPlaylistTrack;
import com.fishmatrix.music.entity.MusicTrack;
import com.fishmatrix.music.error.NotFoundException;
import com.fishmatrix.music.mapper.MusicPlaylistMapper;
import com.fishmatrix.music.mapper.MusicPlaylistTrackMapper;
import com.fishmatrix.music.mapper.MusicTrackMapper;
import com.fishmatrix.music.model.Playlist;
import com.fishmatrix.music.model.PlaylistDetail;
import com.fishmatrix.music.model.PlaylistRequest;
import com.fishmatrix.music.model.TrackSummary;
import com.fishmatrix.music.util.Validation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final MusicPlaylistMapper playlistMapper;
    private final MusicPlaylistTrackMapper playlistTrackMapper;
    private final MusicTrackMapper trackMapper;
    private final MusicLibraryService libraryService;

    public List<Playlist> list(CurrentUser viewer) {
        return playlistMapper.selectByOwner(viewer.id()).stream()
                .map(ViewConverters::toPlaylist).toList();
    }

    public PlaylistDetail get(long playlistId, CurrentUser viewer) {
        MusicPlaylist playlist = requireOwned(playlistId, viewer);
        List<Long> trackIds = playlistTrackMapper.selectTrackIds(playlistId);
        Playlist view = ViewConverters.toPlaylist(playlist);
        if (trackIds.isEmpty()) {
            return new PlaylistDetail(view, List.of());
        }
        // 一次查出全部曲目本体，再按歌单里的排序过滤不可读的项，避免逐条查询。
        Map<Long, MusicTrack> byId = trackMapper.selectByIds(trackIds).stream()
                .collect(Collectors.toMap(MusicTrack::getId, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
        List<TrackSummary> tracks = trackIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(track -> canRead(track, viewer))
                .map(track -> libraryService.summary(track, viewer))
                .toList();
        return new PlaylistDetail(view, tracks);
    }

    @Transactional
    public Playlist create(PlaylistRequest request, CurrentUser viewer) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        MusicPlaylist playlist = new MusicPlaylist();
        playlist.setOwnerId(viewer.id());
        playlist.setName(Validation.text(request.name(), 64, "歌单名称", true));
        playlist.setDescription(Validation.truncate(request.description(), 255));
        playlistMapper.insert(playlist);
        return ViewConverters.toPlaylist(playlist);
    }

    @Transactional
    public Playlist update(long playlistId, PlaylistRequest request, CurrentUser viewer) {
        MusicPlaylist playlist = requireOwned(playlistId, viewer);
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        if (request.name() != null) {
            playlist.setName(Validation.text(request.name(), 64, "歌单名称", true));
        }
        if (request.description() != null) {
            playlist.setDescription(Validation.truncate(request.description(), 255));
        }
        playlistMapper.updateById(playlist);
        return ViewConverters.toPlaylist(playlistMapper.selectWithCount(playlistId));
    }

    @Transactional
    public void delete(long playlistId, CurrentUser viewer) {
        requireOwned(playlistId, viewer);
        playlistTrackMapper.delete(Wrappers.<MusicPlaylistTrack>lambdaQuery()
                .eq(MusicPlaylistTrack::getPlaylistId, playlistId));
        playlistMapper.deleteById(playlistId);
    }

    @Transactional
    public void addTrack(long playlistId, long trackId, CurrentUser viewer) {
        requireOwned(playlistId, viewer);
        libraryService.requireReadable(trackId, viewer);
        Integer max = playlistTrackMapper.selectMaxPosition(playlistId);
        MusicPlaylistTrack link = new MusicPlaylistTrack();
        link.setPlaylistId(playlistId);
        link.setTrackId(trackId);
        link.setPositionNo((max == null ? 0 : max) + 1);
        try {
            playlistTrackMapper.insert(link);
        } catch (DuplicateKeyException exception) {
            // 重复加入视为成功，保持幂等
            return;
        }
        touch(playlistId);
    }

    @Transactional
    public void removeTrack(long playlistId, long trackId, CurrentUser viewer) {
        requireOwned(playlistId, viewer);
        int removed = playlistTrackMapper.delete(Wrappers.<MusicPlaylistTrack>lambdaQuery()
                .eq(MusicPlaylistTrack::getPlaylistId, playlistId)
                .eq(MusicPlaylistTrack::getTrackId, trackId));
        if (removed > 0) {
            touch(playlistId);
        }
    }

    /** 只更新 updated_at：置一个只有 id 的补丁，自动填充负责写入时间。 */
    private void touch(long playlistId) {
        MusicPlaylist patch = new MusicPlaylist();
        patch.setId(playlistId);
        playlistMapper.updateById(patch);
    }

    private MusicPlaylist requireOwned(long playlistId, CurrentUser viewer) {
        MusicPlaylist playlist = playlistMapper.selectWithCount(playlistId);
        if (playlist == null || !Objects.equals(playlist.getOwnerId(), viewer.id())) {
            throw new NotFoundException("歌单不存在");
        }
        return playlist;
    }

    private boolean canRead(MusicTrack track, CurrentUser viewer) {
        if (track.getScope().isPublic()) {
            return track.getStatus().isVisible()
                    || viewer.hasPermission("music.content.manage.any");
        }
        return Objects.equals(track.getOwnerId(), viewer.id())
                || libraryService.hasApprovedAccess(track.getId(), viewer.id());
    }
}
