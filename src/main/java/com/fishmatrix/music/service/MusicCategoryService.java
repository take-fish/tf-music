package com.fishmatrix.music.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.music.converter.ViewConverters;
import com.fishmatrix.music.entity.MusicCategory;
import com.fishmatrix.music.entity.MusicTrack;
import com.fishmatrix.music.mapper.MusicCategoryMapper;
import com.fishmatrix.music.mapper.MusicTrackMapper;
import com.fishmatrix.music.model.Category;
import com.fishmatrix.music.model.CategoryRequest;
import com.fishmatrix.music.util.Validation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MusicCategoryService {

    private final MusicCategoryMapper categoryMapper;
    private final MusicTrackMapper trackMapper;

    public List<Category> list() {
        return categoryMapper.selectList(Wrappers.<MusicCategory>lambdaQuery()
                        .orderByAsc(MusicCategory::getSortOrder)
                        .orderByAsc(MusicCategory::getId))
                .stream().map(ViewConverters::toCategory).toList();
    }

    @Transactional
    public Category create(CategoryRequest request) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        MusicCategory category = new MusicCategory();
        category.setName(Validation.text(request.name(), 64, "分类名称", true));
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        categoryMapper.insert(category);
        return ViewConverters.toCategory(category);
    }

    @Transactional
    public Category update(long id, CategoryRequest request) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        MusicCategory existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw ApiException.badRequest("分类不存在");
        }
        if (request.name() != null) {
            existing.setName(Validation.text(request.name(), 64, "分类名称", true));
        }
        if (request.sortOrder() != null) {
            existing.setSortOrder(request.sortOrder());
        }
        categoryMapper.updateById(existing);
        return ViewConverters.toCategory(existing);
    }

    @Transactional
    public void delete(long id) {
        // 分类被删后曲目归为未分类：先置空引用再删行，避免留下悬空 category_id。
        trackMapper.update(null, Wrappers.<MusicTrack>lambdaUpdate()
                .eq(MusicTrack::getCategoryId, id)
                .set(MusicTrack::getCategoryId, null));
        categoryMapper.deleteById(id);
    }
}
