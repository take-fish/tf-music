package com.fishmatrix.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.music.domain.AccessRequestStatus;
import com.fishmatrix.music.entity.MusicAccessRequest;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MusicAccessRequestMapper extends BaseMapper<MusicAccessRequest> {

    /** 详情视图：带曲目名与曲目 owner 昵称。 */
    MusicAccessRequest selectViewById(@Param("id") long id);

    MusicAccessRequest selectByTrackAndRequester(
            @Param("trackId") long trackId, @Param("requesterId") long requesterId);

    List<MusicAccessRequest> selectByRequester(@Param("requesterId") long requesterId);

    List<MusicAccessRequest> selectByOwner(
            @Param("ownerId") long ownerId, @Param("status") AccessRequestStatus status);

    /** 该曲目是否已对某用户放行（APPROVED）。 */
    long countApproved(@Param("trackId") long trackId, @Param("requesterId") long requesterId);

    int deleteByTrack(@Param("trackId") long trackId);
}
