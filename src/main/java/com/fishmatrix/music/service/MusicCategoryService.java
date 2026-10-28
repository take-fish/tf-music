package com.fishmatrix.music.service;

import com.fishmatrix.music.model.Category;
import com.fishmatrix.music.model.CategoryRequest;
import com.fishmatrix.music.repository.CategoryRepository;
import com.fishmatrix.music.util.Validation;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MusicCategoryService {

    private final CategoryRepository repository;

    public List<Category> list() {
        return repository.findAll();
    }

    @Transactional
    public Category create(CategoryRequest request) {
        if (request == null) throw new IllegalArgumentException("请求体不能为空");
        String name = Validation.text(request.name(), 64, "分类名称", true);
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        long id = repository.insert(name, sortOrder, Instant.now());
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public Category update(long id, CategoryRequest request) {
        if (request == null) throw new IllegalArgumentException("请求体不能为空");
        Category existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("分类不存在"));
        String name = request.name() == null
                ? existing.name() : Validation.text(request.name(), 64, "分类名称", true);
        int sortOrder = request.sortOrder() == null ? existing.sortOrder() : request.sortOrder();
        repository.update(id, name, sortOrder);
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public void delete(long id) {
        repository.delete(id);
    }
}
