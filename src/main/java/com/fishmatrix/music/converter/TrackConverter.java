package com.fishmatrix.music.converter;

import com.fishmatrix.music.entity.MusicTrack;
import com.fishmatrix.music.model.TrackSummary;

/**
 * 实体到对外视图的转换。
 *
 * <p>mine / canEdit / canDelete 由服务层按当前用户判定后传入，与重构前
 * {@code MusicLibraryService.summary(...)} 的计算口径保持一致。</p>
 */
public final class TrackConverter {

    private TrackConverter() {
    }

    public static TrackSummary toSummary(MusicTrack track, boolean mine, boolean canEdit,
            boolean canDelete) {
        return new TrackSummary(
                track.getId(),
                track.getTitle(),
                track.getArtist(),
                track.getAlbum(),
                track.getDurationSec() == null ? 0 : track.getDurationSec(),
                track.getScope(),
                track.getOwnerId(),
                track.getOwnerNickname(),
                track.getCategoryId(),
                track.getCategoryName(),
                track.getStatus(),
                track.getContentType(),
                track.getFileSize() == null ? 0 : track.getFileSize(),
                mine,
                canEdit,
                canDelete,
                track.getCreatedAt(),
                track.getUpdatedAt());
    }
}
