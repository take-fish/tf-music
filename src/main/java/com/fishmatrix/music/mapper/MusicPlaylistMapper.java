package com.fishmatrix.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.music.entity.MusicPlaylist;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MusicPlaylistMapper extends BaseMapper<MusicPlaylist> {

    /** 歌单列表，带曲目数；track_count 对应实体的 @TableField(exist = false) 字段。 */
    List<MusicPlaylist> selectByOwner(@Param("ownerId") long ownerId);

    MusicPlaylist selectWithCount(@Param("id") long id);
}
