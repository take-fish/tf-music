package com.fishmatrix.music.service;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.common.storage.FileStorage;
import com.fishmatrix.common.storage.StoredFile;
import com.fishmatrix.music.model.NotFoundException;
import com.fishmatrix.music.model.ShareLink;
import com.fishmatrix.music.model.TrackPage;
import com.fishmatrix.music.model.TrackStream;
import com.fishmatrix.music.model.TrackSummary;
import com.fishmatrix.music.model.TrackUpdateRequest;
import com.fishmatrix.music.repository.CategoryRepository;
import com.fishmatrix.music.repository.TrackRepository;
import com.fishmatrix.music.repository.TrackRepository.TrackRecord;
import com.fishmatrix.music.util.Validation;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MusicLibraryService {

    public static final String SCOPE_PUBLIC = "PUBLIC";
    public static final String SCOPE_PRIVATE = "PRIVATE";
    public static final String STATUS_VISIBLE = "VISIBLE";
    public static final String STATUS_HIDDEN = "HIDDEN";

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

    private final TrackRepository trackRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorage storage;
    private final long maxUploadBytes;

    public MusicLibraryService(
            TrackRepository trackRepository,
            CategoryRepository categoryRepository,
            FileStorage storage,
            @Value("${app.music.max-upload-bytes:52428800}") long maxUploadBytes) {
        this.trackRepository = trackRepository;
        this.categoryRepository = categoryRepository;
        this.storage = storage;
        this.maxUploadBytes = maxUploadBytes;
    }

    public TrackPage listPublic(Long categoryId, String keyword, int page, int size,
            CurrentUser viewer) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        List<TrackSummary> tracks = trackRepository
                .findPublic(categoryId, keyword, safePage * safeSize, safeSize)
                .stream().map(record -> summary(record, viewer)).toList();
        return new TrackPage(tracks,
                trackRepository.countPublic(categoryId, keyword), safePage, safeSize);
    }

    public List<TrackSummary> listMine(CurrentUser viewer) {
        return trackRepository.findOwned(viewer.id()).stream()
                .map(record -> summary(record, viewer)).toList();
    }

    public List<TrackSummary> listShared(CurrentUser viewer) {
        return trackRepository.findShared(viewer.id()).stream()
                .map(record -> summary(record, viewer)).toList();
    }

    public TrackSummary getTrack(long trackId, CurrentUser viewer) {
        return summary(requireReadable(trackId, viewer), viewer);
    }

    @Transactional
    public TrackSummary importTrack(String scope, String title, String artist, String album,
            Integer durationSec, Long categoryId, MultipartFile file, CurrentUser viewer) {
        String normalizedScope = normalizeScope(scope);
        if (SCOPE_PUBLIC.equals(normalizedScope)) {
            requirePermission(viewer, "music.public.upload");
        } else {
            requirePermission(viewer, "music.private.manage");
        }
        if (categoryId != null) {
            requirePermission(viewer, "music.category.manage");
            requireCategory(categoryId);
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的音频文件");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new IllegalArgumentException("文件大小超过限制");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(filename);
        String contentType = AUDIO_TYPES.get(extension);
        if (contentType == null) {
            throw new IllegalArgumentException("仅支持 mp3/m4a/aac/ogg/wav/flac/webm 音频");
        }

        StoredFile stored;
        try (InputStream input = file.getInputStream()) {
            stored = storage.store(input, filename, contentType,
                    "music/tracks/" + normalizedScope.toLowerCase(Locale.ROOT), maxUploadBytes);
        } catch (IOException exception) {
            throw new IllegalArgumentException("文件读取失败");
        }

        String fallbackTitle = stripExtension(filename);
        String cleanTitle = Validation.truncate(
                title == null || title.isBlank() ? fallbackTitle : title, 255);
        if (cleanTitle.isEmpty()) {
            cleanTitle = "未命名曲目";
        }
        String cleanArtist = Validation.truncate(artist, 128);
        String cleanAlbum = Validation.truncate(album, 128);
        int duration = durationSec == null ? 0 : Math.max(0, durationSec);
        Instant now = Instant.now();
        long trackId;
        try {
            trackId = trackRepository.insertTrack(
                    cleanTitle,
                    cleanArtist,
                    cleanAlbum,
                    duration,
                    normalizedScope,
                    viewer.id(),
                    Validation.truncate(viewer.nickname(), 32),
                    categoryId,
                    Validation.truncate(filename, 255),
                    stored.storageKey(),
                    stored.contentType(),
                    stored.size(),
                    stored.sha256(),
                    now);
        } catch (RuntimeException exception) {
            storage.delete(stored.storageKey());
            throw exception;
        }
        return summary(requireRecord(trackId), viewer);
    }

    @Transactional
    public TrackSummary update(long trackId, TrackUpdateRequest request, CurrentUser viewer) {
        TrackRecord track = requireRecord(trackId);
        boolean manager = viewer.hasPermission("music.content.manage.any");
        boolean owner = track.isOwnedBy(viewer.id());
        if (track.isPublic() && !owner && !manager) {
            throw ApiException.forbidden("没有管理这首曲目的权限");
        }
        if (!track.isPublic() && !owner) {
            throw new NotFoundException("曲目不存在");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }

        String title = request.title() == null
                ? track.title()
                : Validation.text(request.title(), 255, "曲目名称", true);
        String artist = request.artist() == null
                ? track.artist() : Validation.truncate(request.artist(), 128);
        String album = request.album() == null
                ? track.album() : Validation.truncate(request.album(), 128);
        int duration = request.durationSec() == null
                ? track.durationSec() : Math.max(0, request.durationSec());

        Long categoryId = track.categoryId();
        if (request.categoryId() != null && !request.categoryId().equals(track.categoryId())) {
            requirePermission(viewer, "music.category.manage");
            requireCategory(request.categoryId());
            categoryId = request.categoryId();
        }
        String status = track.status();
        if (request.status() != null && !request.status().equalsIgnoreCase(track.status())) {
            requirePermission(viewer, "music.content.manage.any");
            status = normalizeStatus(request.status());
        }
        trackRepository.updateMetadata(trackId, title, artist, album, duration, categoryId,
                status, Instant.now());
        return summary(requireRecord(trackId), viewer);
    }

    @Transactional
    public void delete(long trackId, CurrentUser viewer) {
        TrackRecord track = requireRecord(trackId);
        boolean manager = viewer.hasPermission("music.content.manage.any");
        boolean owner = track.isOwnedBy(viewer.id());
        if (!track.isPublic() && !owner) {
            throw new NotFoundException("曲目不存在");
        }
        if (track.isPublic() && !owner && !manager) {
            throw ApiException.forbidden("没有删除这首曲目的权限");
        }
        trackRepository.deleteTrack(trackId);
        if (track.storageKey() != null) {
            storage.delete(track.storageKey());
        }
    }

    public ShareLink share(long trackId, CurrentUser viewer) {
        TrackRecord track = requirePrivateOwner(trackId, viewer);
        if (track.shareToken() != null && !track.shareToken().isBlank()) {
            return new ShareLink(track.shareToken());
        }
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        trackRepository.updateShareToken(trackId, token, Instant.now());
        return new ShareLink(token);
    }

    public TrackSummary previewByShareToken(String shareToken, CurrentUser viewer) {
        TrackRecord track = trackRepository.findByShareToken(shareToken)
                .filter(record -> SCOPE_PRIVATE.equals(record.scope()))
                .orElseThrow(() -> new NotFoundException("分享链接无效"));
        return summary(track, viewer);
    }

    public TrackStream stream(long trackId, CurrentUser viewer) {
        TrackRecord track = requireReadable(trackId, viewer);
        if (track.storageKey() == null || track.storageKey().isBlank()) {
            throw new NotFoundException("音频文件不存在");
        }
        return new TrackStream(storage.load(track.storageKey()),
                track.contentType(), track.fileSize(), track.sourceFilename());
    }

    public TrackRecord requireReadable(long trackId, CurrentUser viewer) {
        TrackRecord track = requireRecord(trackId);
        if (track.isPrivate()) {
            if (track.isOwnedBy(viewer.id())
                    || trackRepository.hasApprovedAccess(trackId, viewer.id())) {
                return track;
            }
            throw new NotFoundException("曲目不存在");
        }
        if (track.isVisible() || viewer.hasPermission("music.content.manage.any")) {
            return track;
        }
        throw new NotFoundException("曲目不存在");
    }

    public TrackRecord requirePrivateOwner(long trackId, CurrentUser viewer) {
        TrackRecord track = requireRecord(trackId);
        if (!track.isPrivate() || !track.isOwnedBy(viewer.id())) {
            throw new NotFoundException("曲目不存在");
        }
        return track;
    }

    public TrackRecord requireRecord(long trackId) {
        return trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException("曲目不存在"));
    }

    public TrackSummary summary(TrackRecord track, CurrentUser viewer) {
        boolean owner = track.isOwnedBy(viewer.id());
        boolean manager = track.isPublic()
                && viewer.hasPermission("music.content.manage.any");
        boolean editable = owner || manager;
        return new TrackSummary(
                track.id(),
                track.title(),
                track.artist(),
                track.album(),
                track.durationSec(),
                track.scope(),
                track.ownerId(),
                track.ownerNickname(),
                track.categoryId(),
                track.categoryName(),
                track.status(),
                track.contentType(),
                track.fileSize(),
                owner,
                editable,
                editable,
                track.createdAt(),
                track.updatedAt());
    }

    private void requireCategory(long categoryId) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("分类不存在"));
    }

    private static void requirePermission(CurrentUser viewer, String permission) {
        if (!viewer.hasPermission(permission)) {
            throw ApiException.forbidden("没有执行这个操作的权限");
        }
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) return SCOPE_PRIVATE;
        String value = scope.strip().toUpperCase(Locale.ROOT);
        if (!SCOPE_PUBLIC.equals(value) && !SCOPE_PRIVATE.equals(value)) {
            throw new IllegalArgumentException("曲库类型不合法");
        }
        return value;
    }

    private static String normalizeStatus(String status) {
        String value = status.strip().toUpperCase(Locale.ROOT);
        if (!STATUS_VISIBLE.equals(value) && !STATUS_HIDDEN.equals(value)) {
            throw new IllegalArgumentException("曲目状态不合法");
        }
        return value;
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
