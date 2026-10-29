package com.fishmatrix.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fishmatrix.music.entity.MusicTrack;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MusicTrackMapper extends BaseMapper<MusicTrack> {

    /**
     * 公共曲库分页查询。
     *
     * <p><b>分页基准</b>：对外 API 的 page 是 0-based（见 {@code TrackController} /
     * {@code MusicLibraryService}），而 MyBatis-Plus 的 {@link Page} 是 1-based，
     * 调用方必须做 {@code page + 1} 换算，组装 {@code TrackPage} 时再把
     * {@code IPage#getCurrent()} 换算回 0-based。</p>
     *
     * <p><b>size 上限</b>：100 的上限由 {@code MusicLibraryService} 钳制，本方法与分页拦截器都不设 maxLimit。</p>
     */
    IPage<MusicTrack> selectPublicPage(
            Page<MusicTrack> page, @Param("categoryId") Long categoryId, @Param("keyword") String keyword);

    /** 详情：带出分类名，与列表口径一致。 */
    MusicTrack selectViewById(@Param("id") long id);

    List<MusicTrack> selectOwned(@Param("ownerId") long ownerId);

    List<MusicTrack> selectShared(@Param("viewerId") long viewerId);

    List<MusicTrack> selectByIds(@Param("ids") List<Long> ids);

    MusicTrack selectByShareToken(@Param("shareToken") String shareToken);
}
