package com.fishmatrix.music.service;

import com.fishmatrix.music.model.NotFoundException;
import com.fishmatrix.music.model.PlaybackRecord;
import com.fishmatrix.music.model.PlaylistDetail;
import com.fishmatrix.music.model.PlaylistSummary;
import com.fishmatrix.music.model.RecordRequest;
import com.fishmatrix.music.model.Track;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class MusicCatalogService {

    private static final int MAX_RECORDS = 100;
    private static final Set<String> ACTIONS =
            Set.of("start", "pause", "resume", "complete", "seek");

    private final Map<String, PlaylistDetail> playlists = new LinkedHashMap<>();
    private final ConcurrentLinkedDeque<PlaybackRecord> records = new ConcurrentLinkedDeque<>();
    private final AtomicLong recordSequence = new AtomicLong();

    public MusicCatalogService() {
        PlaylistDetail pipeline = new PlaylistDetail(
                "fishmatrix-api",
                "fishmatrix-api",
                128,
                List.of(
                        new Track(
                                "quiet-night",
                                "静夜思（Demo）",
                                "Demo Build",
                                120,
                                "prepare",
                                List.of("床前明月光", "疑是地上霜", "举头望明月", "低头思故乡")),
                        new Track(
                                "river-echo",
                                "River Echo (Instrumental)",
                                "Pipeline Sounds",
                                184,
                                "compile",
                                List.of()),
                        new Track(
                                "deploy-waltz",
                                "Deploy Waltz",
                                "CI Ensemble",
                                156,
                                "publish",
                                List.of())));
        playlists.put(pipeline.id(), pipeline);
    }

    public List<PlaylistSummary> playlists() {
        return playlists.values().stream()
                .map(playlist -> new PlaylistSummary(
                        playlist.id(),
                        playlist.name(),
                        "持续集成构建流水线",
                        playlist.buildNumber(),
                        playlist.tracks().size()))
                .toList();
    }

    public PlaylistDetail playlist(String playlistId) {
        PlaylistDetail playlist = playlists.get(playlistId);
        if (playlist == null) {
            throw new NotFoundException("构建流水线不存在");
        }
        return playlist;
    }

    public PlaybackRecord record(String playlistId, RecordRequest request) {
        PlaylistDetail playlist = playlist(playlistId);
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Track track = playlist.tracks().stream()
                .filter(item -> item.id().equals(request.trackId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("构建任务不存在"));
        if (request.userId() == null || !request.userId().matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("userId 格式不合法");
        }
        if (!ACTIONS.contains(request.action())) {
            throw new IllegalArgumentException("不支持的操作");
        }
        if (request.positionSec() < 0) {
            throw new IllegalArgumentException("播放位置不合法");
        }
        PlaybackRecord record = new PlaybackRecord(
                recordSequence.incrementAndGet(),
                playlistId,
                track.id(),
                request.userId(),
                request.action(),
                request.positionSec(),
                System.currentTimeMillis());
        records.addFirst(record);
        while (records.size() > MAX_RECORDS) {
            records.pollLast();
        }
        return record;
    }

    public List<PlaybackRecord> records(int limit) {
        int safeLimit = Math.min(MAX_RECORDS, Math.max(1, limit));
        List<PlaybackRecord> result = new ArrayList<>();
        for (PlaybackRecord record : records) {
            if (result.size() >= safeLimit) break;
            result.add(record);
        }
        return result;
    }
}
