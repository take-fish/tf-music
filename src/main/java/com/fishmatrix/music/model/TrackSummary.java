package com.fishmatrix.music.model;

import com.fishmatrix.music.domain.TrackScope;
import com.fishmatrix.music.domain.TrackStatus;
import java.time.Instant;

/**
 * 曲目列表项。scope/owner/status 决定可见性与可管理性，
 * canEdit/canDelete 由服务端按当前用户计算，前端不自行推断。
 */
public record TrackSummary(
        long id,
        String title,
        String artist,
        String album,
        int durationSec,
        TrackScope scope,
        Long ownerId,
        String ownerNickname,
        Long categoryId,
        String categoryName,
        TrackStatus status,
        String contentType,
        long fileSize,
        boolean mine,
        boolean canEdit,
        boolean canDelete,
        Instant createdAt,
        Instant updatedAt) {
}
