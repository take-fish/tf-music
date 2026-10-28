package com.fishmatrix.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.common.storage.FileStorage;
import com.fishmatrix.common.storage.StoredFile;
import com.fishmatrix.music.converter.TrackConverter;
import com.fishmatrix.music.domain.TrackScope;
import com.fishmatrix.music.domain.TrackStatus;
import com.fishmatrix.music.entity.MusicTrack;
import com.fishmatrix.music.entity.MusicPlaylistTrack;
import com.fishmatrix.music.error.NotFoundException;
import com.fishmatrix.music.mapper.MusicAccessRequestMapper;
import com.fishmatrix.music.mapper.MusicCategoryMapper;
import com.fishmatrix.music.mapper.MusicPlaybackMapper;
import com.fishmatrix.music.mapper.MusicPlaylistTrackMapper;
import com.fishmatrix.music.mapper.MusicTrackMapper;
import com.fishmatrix.music.model.ShareLink;
import com.fishmatrix.music.model.TrackPage;
import com.fishmatrix.music.model.TrackStream;
import com.fishmatrix.music.model.TrackSummary;
import com.fishmatrix.music.model.TrackUpdateRequest;
import com.fishmatrix.music.util.Validation;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class MusicLibraryService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_PAGE_SIZE = 100;
    private static final Map<String, String> AUDIO_TYPES = Map.of(
            "mp3", "audio/mpeg",
            "m4a", "audio/mp4",
            "aac", "audio/aac",
            "ogg", "audio/ogg",
            "wav", "audio/wav",
            "flac", "audio/flac",
            "webm", "audio/webm");

    private final MusicTrackMapper trackMapper;
    private final MusicCategoryMapper categoryMapper;
    private final MusicPlaylistTrackMapper playlistTrackMapper;
    private final MusicAccessRequestMapper accessRequestMapper;
    private final MusicPlaybackMapper playbackMapper;
    private final FileStorage storage;
    private final long maxUploadBytes;

    public MusicLibraryService(
            MusicTrackMapper trackMapper,
            MusicCategoryMapper categoryMapper,
            MusicPlaylistTrackMapper playlistTrackMapper,
            MusicAccessRequestMapper accessRequestMapper,
            MusicPlaybackMapper playbackMapper,
            FileStorage storage,
            @Value("${app.music.max-upload-bytes:52428800}") long maxUploadBytes) {
        this.trackMapper = trackMapper;
        this.categoryMapper = categoryMapper;
        this.playlistTrackMapper = playlistTrackMapper;
        this.accessRequestMapper = accessRequestMapper;
        this.playbackMapper = playbackMapper;
        this.storage = storage;
        this.maxUploadBytes = maxUploadBytes;
    }

    public TrackPage listPublic(Long categoryId, String keyword, int page, int size,
            CurrentUser viewer) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        // 对外 page 是 0-based，MyBatis-Plus 的 Page 是 1-based；总数由分页拦截器统计。
        IPage<MusicTrack> result = trackMapper.selectPublicPage(
                new Page<>(safePage + 1L, safeSize), categoryId, keyword);
        List<TrackSummary> tracks = result.getRecords().stream()
                .map(track -> summary(track, viewer)).toList();
        return new TrackPage(tracks, result.getTotal(), safePage, safeSize);
    }

    public List<TrackSummary> listMine(CurrentUser viewer) {
        return trackMapper.selectOwned(viewer.id()).stream()
                .map(track -> summary(track, viewer)).toList();
    }

    public List<TrackSummary> listShared(CurrentUser viewer) {
        return trackMapper.selectShared(viewer.id()).stream()
                .map(track -> summary(track, viewer)).toList();
    }

    public TrackSummary getTrack(long trackId, CurrentUser viewer) {
        return summary(requireReadable(trackId, viewer), viewer);
    }

    @Transactional
    public TrackSummary importTrack(String scope, String title, String artist, String album,
            Integer durationSec, Long categoryId, MultipartFile file, CurrentUser viewer) {
        TrackScope normalizedScope = normalizeScope(scope);
        if (normalizedScope.isPublic()) {
            requirePermission(viewer, "music.public.upload");
        } else {
            requirePermission(viewer, "music.private.manage");
        }
        if (categoryId != null) {
            requirePermission(viewer, "music.category.manage");
            requireCategory(categoryId);
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择要上传的音频文件");
        }
        if (file.getSize() > maxUploadBytes) {
            throw ApiException.badRequest("文件大小超过限制");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(filename);
        String contentType = AUDIO_TYPES.get(extension);
        if (contentType == null) {
            throw ApiException.badRequest("仅支持 mp3/m4a/aac/ogg/wav/flac/webm 音频");
        }

        StoredFile stored;
        try (InputStream input = file.getInputStream()) {
            stored = storage.store(input, filename, contentType,
                    "music/tracks/" + normalizedScope.name().toLowerCase(Locale.ROOT),
                    maxUploadBytes);
        } catch (IOException exception) {
            throw ApiException.badRequest("文件读取失败");
        }

        String fallbackTitle = stripExtension(filename);
        String cleanTitle = Validation.truncate(
                title == null || title.isBlank() ? fallbackTitle : title, 255);
        if (cleanTitle.isEmpty()) {
            cleanTitle = "未命名曲目";
        }

        MusicTrack track = new MusicTrack();
        track.setTitle(cleanTitle);
        track.setArtist(Validation.truncate(artist, 128));
        track.setAlbum(Validation.truncate(album, 128));
        track.setDurationSec(durationSec == null ? 0 : Math.max(0, durationSec));
        track.setScope(normalizedScope);
        track.setOwnerId(viewer.id());
        track.setOwnerNickname(Validation.truncate(viewer.nickname(), 32));
        track.setCategoryId(categoryId);
        track.setStatus(TrackStatus.VISIBLE);
        track.setSourceFilename(Validation.truncate(filename, 255));
        track.setStorageKey(stored.storageKey());
        track.setContentType(stored.contentType());
        track.setFileSize(stored.size());
        track.setSha256(stored.sha256());
        try {
            trackMapper.insert(track);
        } catch (RuntimeException exception) {
            storage.delete(stored.storageKey());
            throw exception;
        }
        log.info("music track imported id={} scope={} owner={}", track.getId(),
                normalizedScope, viewer.id());
        return summary(requireRecord(track.getId()), viewer);
    }

    @Transactional
    public TrackSummary update(long trackId, TrackUpdateRequest request, CurrentUser viewer) {
        MusicTrack track = requireRecord(trackId);
        boolean manager = viewer.hasPermission("music.content.manage.any");
        boolean owner = isOwnedBy(track, viewer.id());
        if (track.getScope().isPublic() && !owner && !manager) {
            throw ApiException.forbidden("没有管理这首曲目的权限");
        }
        if (track.getScope().isPrivate() && !owner) {
            throw new NotFoundException("曲目不存在");
        }
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }

        track.setTitle(request.title() == null
                ? track.getTitle()
                : Validation.text(request.title(), 255, "曲目名称", true));
        if (request.artist() != null) {
            track.setArtist(Validation.truncate(request.artist(), 128));
        }
        if (request.album() != null) {
            track.setAlbum(Validation.truncate(request.album(), 128));
        }
        if (request.durationSec() != null) {
            track.setDurationSec(Math.max(0, request.durationSec()));
        }
        if (request.categoryId() != null
                && !request.categoryId().equals(track.getCategoryId())) {
            requirePermission(viewer, "music.category.manage");
            requireCategory(request.categoryId());
            track.setCategoryId(request.categoryId());
        }
        if (request.status() != null
                && !request.status().equalsIgnoreCase(track.getStatus().name())) {
            requirePermission(viewer, "music.content.manage.any");
            track.setStatus(normalizeStatus(request.status()));
        }
        trackMapper.updateById(track);
        return summary(requireRecord(trackId), viewer);
    }

    @Transactional
    public void delete(long trackId, CurrentUser viewer) {
        MusicTrack track = requireRecord(trackId);
        boolean manager = viewer.hasPermission("music.content.manage.any");
        boolean owner = isOwnedBy(track, viewer.id());
        if (track.getScope().isPrivate() && !owner) {
            throw new NotFoundException("曲目不存在");
        }
        if (track.getScope().isPublic() && !owner && !manager) {
            throw ApiException.forbidden("没有删除这首曲目的权限");
        }
        // 关联表先清，最后删曲目本体；播放记录与访问申请没有外键，必须显式删除。
        playlistTrackMapper.delete(Wrappers.<MusicPlaylistTrack>lambdaQuery()
                .eq(MusicPlaylistTrack::getTrackId, trackId));
        accessRequestMapper.deleteByTrack(trackId);
        playbackMapper.deleteByTrack(trackId);
        trackMapper.deleteById(trackId);
        if (track.getStorageKey() != null) {
            storage.delete(track.getStorageKey());
        }
    }

    public ShareLink share(long trackId, CurrentUser viewer) {
        MusicTrack track = requirePrivateOwner(trackId, viewer);
        if (track.getShareToken() != null && !track.getShareToken().isBlank()) {
            return new ShareLink(track.getShareToken());
        }
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        MusicTrack patch = new MusicTrack();
        patch.setId(trackId);
        patch.setShareToken(token);
        trackMapper.updateById(patch);
        return new ShareLink(token);
    }

    public TrackSummary previewByShareToken(String shareToken, CurrentUser viewer) {
        MusicTrack track = trackMapper.selectByShareToken(shareToken);
        if (track == null || !track.getScope().isPrivate()) {
            throw new NotFoundException("分享链接无效");
        }
        return summary(track, viewer);
    }

    public TrackStream stream(long trackId, CurrentUser viewer) {
        MusicTrack track = requireReadable(trackId, viewer);
        if (track.getStorageKey() == null || track.getStorageKey().isBlank()) {
            throw new NotFoundException("音频文件不存在");
        }
        return new TrackStream(storage.load(track.getStorageKey()),
                track.getContentType(), track.getFileSize() == null ? 0 : track.getFileSize(),
                track.getSourceFilename());
    }

    public MusicTrack requireReadable(long trackId, CurrentUser viewer) {
        MusicTrack track = requireRecord(trackId);
        if (track.getScope().isPrivate()) {
            if (isOwnedBy(track, viewer.id())
                    || hasApprovedAccess(trackId, viewer.id())) {
                return track;
            }
            throw new NotFoundException("曲目不存在");
        }
        if (track.getStatus().isVisible()
                || viewer.hasPermission("music.content.manage.any")) {
            return track;
        }
        throw new NotFoundException("曲目不存在");
    }

    /** 曲目是否已对某用户放行（存在 APPROVED 的访问申请）。 */
    public boolean hasApprovedAccess(long trackId, long viewerId) {
        return accessRequestMapper.countApproved(trackId, viewerId) > 0;
    }

    public MusicTrack requirePrivateOwner(long trackId, CurrentUser viewer) {
        MusicTrack track = requireRecord(trackId);
        if (!track.getScope().isPrivate() || !isOwnedBy(track, viewer.id())) {
            throw new NotFoundException("曲目不存在");
        }
        return track;
    }

    public MusicTrack requireRecord(long trackId) {
        MusicTrack track = trackMapper.selectViewById(trackId);
        if (track == null) {
            throw new NotFoundException("曲目不存在");
        }
        return track;
    }

    public TrackSummary summary(MusicTrack track, CurrentUser viewer) {
        boolean owner = isOwnedBy(track, viewer.id());
        boolean manager = track.getScope().isPublic()
                && viewer.hasPermission("music.content.manage.any");
        boolean editable = owner || manager;
        return TrackConverter.toSummary(track, owner, editable, editable);
    }

    private void requireCategory(long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw ApiException.badRequest("分类不存在");
        }
    }

    private static boolean isOwnedBy(MusicTrack track, long userId) {
        return track.getOwnerId() != null && track.getOwnerId() == userId;
    }

    private static void requirePermission(CurrentUser viewer, String permission) {
        if (!viewer.hasPermission(permission)) {
            throw ApiException.forbidden("没有执行这个操作的权限");
        }
    }

    static TrackScope normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return TrackScope.PRIVATE;
        }
        try {
            return TrackScope.valueOf(scope.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("曲库类型不合法");
        }
    }

    static TrackStatus normalizeStatus(String status) {
        try {
            return TrackStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw ApiException.badRequest("曲目状态不合法");
        }
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
