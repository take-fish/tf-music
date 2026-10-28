package com.fishmatrix.music.converter;

import com.fishmatrix.music.entity.MusicAccessRequest;
import com.fishmatrix.music.entity.MusicCategory;
import com.fishmatrix.music.entity.MusicPlayback;
import com.fishmatrix.music.entity.MusicPlaylist;
import com.fishmatrix.music.model.AccessRequestView;
import com.fishmatrix.music.model.Category;
import com.fishmatrix.music.model.PlaybackRecord;
import com.fishmatrix.music.model.Playlist;

/** 其余实体的视图转换，都是纯字段搬运，无业务判断。 */
public final class ViewConverters {

    private ViewConverters() {
    }

    public static Category toCategory(MusicCategory category) {
        return new Category(category.getId(), category.getName(),
                category.getSortOrder() == null ? 0 : category.getSortOrder());
    }

    public static Playlist toPlaylist(MusicPlaylist playlist) {
        return new Playlist(playlist.getId(), playlist.getName(), playlist.getDescription(),
                playlist.getTrackCount() == null ? 0 : playlist.getTrackCount(),
                playlist.getUpdatedAt());
    }

    public static AccessRequestView toAccessRequest(MusicAccessRequest request) {
        return new AccessRequestView(
                request.getId(),
                request.getTrackId(),
                request.getTrackTitle(),
                request.getOwnerId(),
                request.getOwnerNickname(),
                request.getRequesterId(),
                request.getRequesterNickname(),
                request.getStatus(),
                request.getMessage(),
                request.getDecisionMessage(),
                request.getCreatedAt(),
                request.getDecidedAt());
    }

    public static PlaybackRecord toPlaybackRecord(MusicPlayback playback) {
        return new PlaybackRecord(
                playback.getId(),
                playback.getTrackId(),
                playback.getTrackTitle(),
                playback.getAction(),
                playback.getPositionSec() == null ? 0 : playback.getPositionSec(),
                playback.getCreatedAt().toEpochMilli());
    }
}
