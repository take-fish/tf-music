package com.fishmatrix.music.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.auth.UserContext;
import com.fishmatrix.common.autoconfigure.FishCommonProperties;
import com.fishmatrix.common.error.GlobalExceptionHandler;
import com.fishmatrix.music.model.NotFoundException;
import com.fishmatrix.music.model.TrackPage;
import com.fishmatrix.music.model.TrackStream;
import com.fishmatrix.music.model.TrackSummary;
import com.fishmatrix.music.service.MusicLibraryService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TrackControllerTest {

    private static final CurrentUser VIEWER = new CurrentUser(
            7L, "listener", "听众", "USER", List.of("USER"),
            Set.of("music.public.upload"));

    @TempDir
    Path tempDir;

    private MusicLibraryService libraryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        libraryService = mock(MusicLibraryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TrackController(libraryService))
                .setControllerAdvice(new GlobalExceptionHandler(new FishCommonProperties()))
                .build();
        UserContext.set(VIEWER);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static TrackSummary summary(long id, String title) {
        return new TrackSummary(id, title, "歌手", "专辑", 180, "PUBLIC", null, "", null, null,
                "VISIBLE", "audio/mpeg", 10, false, false, false,
                Instant.parse("2026-10-20T09:00:00Z"), Instant.parse("2026-10-20T09:00:00Z"));
    }

    private TrackStream stream() throws Exception {
        Path file = tempDir.resolve("sample.mp3");
        Files.writeString(file, "0123456789");
        return new TrackStream(new FileSystemResource(file), "audio/mpeg", 10, "sample.mp3");
    }

    @Test
    void listsPublicTracks() throws Exception {
        when(libraryService.listPublic(eq(null), eq(null), eq(0), eq(20), any()))
                .thenReturn(new TrackPage(List.of(summary(1L, "夜曲")), 1, 0, 20));

        mockMvc.perform(get("/api/v1/music/tracks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks[0].title").value("夜曲"))
                .andExpect(jsonPath("$.tracks[0].contentType").value("audio/mpeg"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void listsPrivateTracks() throws Exception {
        when(libraryService.listMine(any())).thenReturn(List.of(summary(2L, "私藏")));

        mockMvc.perform(get("/api/v1/music/tracks").param("scope", "private"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks[0].title").value("私藏"));
    }

    @Test
    void importsAudio() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "song.mp3", "audio/mpeg", "abc".getBytes());
        when(libraryService.importTrack(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(summary(9L, "song"));

        mockMvc.perform(multipart("/api/v1/music/tracks/import")
                        .file(file)
                        .param("artist", "歌手"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void streamsFullFile() throws Exception {
        when(libraryService.stream(eq(1L), any())).thenReturn(stream());

        mockMvc.perform(get("/api/v1/music/tracks/1/stream"))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Length", "10"));
    }

    @Test
    void streamsPartialContent() throws Exception {
        when(libraryService.stream(eq(1L), any())).thenReturn(stream());

        mockMvc.perform(get("/api/v1/music/tracks/1/stream")
                        .header("Range", "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 2-5/10"))
                .andExpect(header().string("Content-Length", "4"));
    }

    @Test
    void rejectsUnsatisfiableRange() throws Exception {
        when(libraryService.stream(eq(1L), any())).thenReturn(stream());

        mockMvc.perform(get("/api/v1/music/tracks/1/stream")
                        .header("Range", "bytes=99-120"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(jsonPath("$.error").value(containsString("RANGE")));
    }

    @Test
    void mapsNotFound() throws Exception {
        when(libraryService.getTrack(eq(99L), any()))
                .thenThrow(new NotFoundException("曲目不存在"));

        mockMvc.perform(get("/api/v1/music/tracks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void rejectsAnonymousRequestAtController() throws Exception {
        UserContext.clear();

        mockMvc.perform(get("/api/v1/music/tracks"))
                .andExpect(status().isUnauthorized());
    }
}
