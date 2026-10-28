package com.fishmatrix.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("music_playlist")
public class MusicPlaylist {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long ownerId;
    private String name;
    private String description;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /** 联表统计带出的曲目数，不是 music_playlist 的列。 */
    @TableField(exist = false)
    private Integer trackCount;
}
