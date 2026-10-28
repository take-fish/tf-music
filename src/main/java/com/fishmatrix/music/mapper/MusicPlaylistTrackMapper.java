package com.fishmatrix.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.music.entity.MusicPlaylistTrack;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 复合主键表（无 @TableId）：写操作走 XML 或 Wrapper。
 * 不要使用 selectById / updateById / deleteById，这些方法在无主键实体上不可用。
 */
@Mapper
public interface MusicPlaylistTrackMapper extends BaseMapper<MusicPlaylistTrack> {

    List<Long> selectTrackIds(@Param("playlistId") long playlistId);

    /** 当前最大排序号，新曲目追加到末尾；空歌单返回 null。 */
    Integer selectMaxPosition(@Param("playlistId") long playlistId);
}
