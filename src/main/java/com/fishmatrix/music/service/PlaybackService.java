package com.fishmatrix.music.service;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.music.model.PlaybackRecord;
import com.fishmatrix.music.model.RecordRequest;
import com.fishmatrix.music.repository.PlaybackRepository;
import java.time.Instant;
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

    private final PlaybackRepository repository;
    private final MusicLibraryService libraryService;

    public PlaybackRecord record(long trackId, RecordRequest request, CurrentUser viewer) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (!ACTIONS.contains(request.action())) {
            throw new IllegalArgumentException("不支持的操作");
        }
        int position = request.positionSec() == null ? 0 : request.positionSec();
        if (position < 0) {
            throw new IllegalArgumentException("播放位置不合法");
        }
        // 只有对当前用户可读的曲目才允许记录播放。
        libraryService.requireReadable(trackId, viewer);
        Instant now = Instant.now();
        long id = repository.insert(viewer.id(), trackId, request.action(), position, now);
        return new PlaybackRecord(id, trackId, null, request.action(), position,
                now.toEpochMilli());
    }

    public List<PlaybackRecord> records(CurrentUser viewer, int limit) {
        int safeLimit = Math.min(MAX_RECORDS, Math.max(1, limit));
        return repository.findByUser(viewer.id(), safeLimit);
    }
}
