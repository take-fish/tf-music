package com.fishmatrix.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.music.entity.MusicPlayback;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MusicPlaybackMapper extends BaseMapper<MusicPlayback> {

    /** 最近播放记录，带曲目名。 */
    List<MusicPlayback> selectRecentByUser(@Param("userId") long userId, @Param("limit") int limit);

    int deleteByTrack(@Param("trackId") long trackId);
}
