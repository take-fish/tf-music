package com.fishmatrix.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** 复合主键 (playlist_id, track_id)：不设 @TableId，写操作走 XML。 */
@Data
@TableName("music_playlist_track")
public class MusicPlaylistTrack {

    private Long playlistId;
    private Long trackId;
    private Integer positionNo;

    @TableField(value = "added_at", fill = FieldFill.INSERT)
    private Instant addedAt;
}
