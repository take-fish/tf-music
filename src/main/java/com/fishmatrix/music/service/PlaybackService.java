package com.fishmatrix.music.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.music.converter.ViewConverters;
import com.fishmatrix.music.entity.MusicPlayback;
import com.fishmatrix.music.mapper.MusicPlaybackMapper;
import com.fishmatrix.music.model.PlaybackRecord;
import com.fishmatrix.music.model.RecordRequest;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaybackService {

    private static final int MAX_RECORDS = 100;
    private static final Set<String> ACTIONS =
            Set.of("start", "pause", "resume", "complete", "seek");

    private final MusicPlaybackMapper playbackMapper;
    private final MusicLibraryService libraryService;

    public PlaybackRecord record(long trackId, RecordRequest request, CurrentUser viewer) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        if (request.action() == null || !ACTIONS.contains(request.action())) {
            throw ApiException.badRequest("不支持的操作");
        }
        int position = request.positionSec() == null ? 0 : request.positionSec();
        if (position < 0) {
            throw ApiException.badRequest("播放位置不合法");
        }
        // 只有对当前用户可读的曲目才允许记录播放。
        libraryService.requireReadable(trackId, viewer);

        MusicPlayback playback = new MusicPlayback();
        playback.setUserId(viewer.id());
        playback.setTrackId(trackId);
        playback.setAction(request.action());
        playback.setPositionSec(position);
        playbackMapper.insert(playback);
        return ViewConverters.toPlaybackRecord(playback);
    }

    public List<PlaybackRecord> records(CurrentUser viewer, int limit) {
        int safeLimit = Math.min(MAX_RECORDS, Math.max(1, limit));
        return playbackMapper.selectRecentByUser(viewer.id(), safeLimit).stream()
                .map(ViewConverters::toPlaybackRecord)
                .toList();
    }
}
