package com.fishmatrix.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fishmatrix.music.domain.TrackScope;
import com.fishmatrix.music.domain.TrackStatus;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("music_track")
public class MusicTrack {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;
    private String artist;
    private String album;
    private Integer durationSec;
    private TrackScope scope;
    private Long ownerId;
    private String ownerNickname;
    private Long categoryId;
    private TrackStatus status;
    private String shareToken;
    private String storageKey;
    private String sourceFilename;
    private String contentType;
    private Long fileSize;
    private String sha256;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /** 联表查询带出的分类名，不是 music_track 的列。 */
    @TableField(exist = false)
    private String categoryName;
}
