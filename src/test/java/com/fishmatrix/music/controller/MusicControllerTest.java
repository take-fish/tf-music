package com.fishmatrix.music.controller;

import com.fishmatrix.common.autoconfigure.FishCommonProperties;
import com.fishmatrix.common.error.GlobalExceptionHandler;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fishmatrix.music.service.MusicCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MusicControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MusicController(new MusicCatalogService()))
                .setControllerAdvice(new GlobalExceptionHandler(new FishCommonProperties()))
                .build();
    }

    @Test
    void listsPlaylists() throws Exception {
        mockMvc.perform(get("/api/v1/music/playlists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playlists[0].id").value("fishmatrix-api"))
                .andExpect(jsonPath("$.playlists[0].trackCount").value(3));
    }

    @Test
    void returnsPlaylistTracks() throws Exception {
        mockMvc.perform(get("/api/v1/music/playlists/fishmatrix-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks[0].title").value("静夜思（Demo）"))
                .andExpect(jsonPath("$.tracks[0].lyrics[0]").value("床前明月光"));
    }

    @Test
    void recordsPlayback() throws Exception {
        mockMvc.perform(post("/api/v1/music/playlists/fishmatrix-api/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackId\":\"quiet-night\",\"userId\":\"user-a\","
                                + "\"action\":\"start\",\"positionSec\":0}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recordId").isNumber());

        mockMvc.perform(get("/api/v1/music/records").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].action").value("start"));
    }

    @Test
    void validatesRecord() throws Exception {
        mockMvc.perform(post("/api/v1/music/playlists/fishmatrix-api/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackId\":\"missing\",\"userId\":\"user-a\","
                                + "\"action\":\"start\",\"positionSec\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void mapsNotFoundPlaylist() throws Exception {
        mockMvc.perform(get("/api/v1/music/playlists/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
