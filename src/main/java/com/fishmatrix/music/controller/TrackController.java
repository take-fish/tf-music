package com.fishmatrix.music.controller;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.auth.UserContext;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.music.model.ShareLink;
import com.fishmatrix.music.model.TrackPage;
import com.fishmatrix.music.model.TrackStream;
import com.fishmatrix.music.model.TrackSummary;
import com.fishmatrix.music.model.TrackUpdateRequest;
import com.fishmatrix.music.service.MusicLibraryService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/music")
@RequiredArgsConstructor
public class TrackController {

    private final MusicLibraryService libraryService;

    @GetMapping("/tracks")
    public TrackPage tracks(
            @RequestParam(defaultValue = "public") String scope,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser viewer = UserContext.require();
        if ("private".equalsIgnoreCase(scope)) {
            List<TrackSummary> tracks = libraryService.listMine(viewer);
            return new TrackPage(tracks, tracks.size(), 0, tracks.size());
        }
        return libraryService.listPublic(categoryId, q, page, size, viewer);
    }

    @GetMapping("/tracks/mine")
    public Map<String, Object> myTracks() {
        return Map.of("tracks", libraryService.listMine(UserContext.require()));
    }

    @GetMapping("/tracks/shared")
    public Map<String, Object> sharedTracks() {
        return Map.of("tracks", libraryService.listShared(UserContext.require()));
    }

    @GetMapping("/tracks/shared/{shareToken}")
    public TrackSummary previewShared(@PathVariable String shareToken) {
        return libraryService.previewByShareToken(shareToken, UserContext.require());
    }

    @GetMapping("/tracks/{trackId}")
    public TrackSummary track(@PathVariable long trackId) {
        return libraryService.getTrack(trackId, UserContext.require());
    }

    @PostMapping(value = "/tracks/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TrackSummary importTrack(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String album,
            @RequestParam(required = false) Integer durationSec,
            @RequestParam(required = false) Long categoryId) {
        return libraryService.importTrack(scope, title, artist, album, durationSec, categoryId,
                file, UserContext.require());
    }

    @PatchMapping("/tracks/{trackId}")
    public TrackSummary updateTrack(@PathVariable long trackId,
            @RequestBody TrackUpdateRequest request) {
        return libraryService.update(trackId, request, UserContext.require());
    }

    @DeleteMapping("/tracks/{trackId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrack(@PathVariable long trackId) {
        libraryService.delete(trackId, UserContext.require());
    }

    @PostMapping("/tracks/{trackId}/share")
    public ShareLink share(@PathVariable long trackId) {
        return libraryService.share(trackId, UserContext.require());
    }

    /** 支持 HTTP Range 的分段流式播放，浏览器拖动进度条时只取需要的字节。 */
    @GetMapping("/tracks/{trackId}/stream")
    public ResponseEntity<ResourceRegion> stream(@PathVariable long trackId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader)
            throws IOException {
        TrackStream stream = libraryService.stream(trackId, UserContext.require());
        Resource resource = stream.resource();
        long size = stream.size() > 0 ? stream.size() : resource.contentLength();
        if (size <= 0) {
            throw ApiException.notFound("音频文件不存在");
        }

        List<HttpRange> ranges = HttpRange.parseRanges(
                rangeHeader == null ? "" : rangeHeader);
        ResourceRegion region;
        HttpStatus status;
        if (ranges.isEmpty()) {
            region = new ResourceRegion(resource, 0, size);
            status = HttpStatus.OK;
        } else {
            if (ranges.size() > 1) {
                throw ApiException.of(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                        "RANGE_NOT_SATISFIABLE", "只支持单段 Range 请求");
            }
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(size);
            long end = range.getRangeEnd(size);
            if (start >= size || start > end) {
                throw ApiException.of(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                        "RANGE_NOT_SATISFIABLE", "请求的字节范围无效");
            }
            region = new ResourceRegion(resource, start, end - start + 1);
            status = HttpStatus.PARTIAL_CONTENT;
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(MediaType.parseMediaType(stream.contentType()));
        if (stream.filename() != null && !stream.filename().isBlank()) {
            builder.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                    .filename(stream.filename(), StandardCharsets.UTF_8).build().toString());
        }
        return builder.body(region);
    }
}
