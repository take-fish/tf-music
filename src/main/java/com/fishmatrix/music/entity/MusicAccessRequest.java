package com.fishmatrix.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fishmatrix.music.domain.AccessRequestStatus;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("music_access_request")
public class MusicAccessRequest {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long trackId;
    private Long ownerId;
    private Long requesterId;
    private String requesterNickname;
    private AccessRequestStatus status;
    private String message;
    private String decisionMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    private Instant decidedAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /** 联表查询带出的曲目名，不是 music_access_request 的列。 */
    @TableField(exist = false)
    private String trackTitle;

    /** 联表查询带出的曲目 owner 昵称，不是 music_access_request 的列。 */
    @TableField(exist = false)
    private String ownerNickname;
}
