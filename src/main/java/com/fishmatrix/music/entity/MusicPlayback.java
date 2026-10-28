package com.fishmatrix.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("music_playback")
public class MusicPlayback {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long trackId;
    private String action;
    private Integer positionSec;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /** 联表查询带出的曲目名，不是 music_playback 的列。 */
    @TableField(exist = false)
    private String trackTitle;
}
