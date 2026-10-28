package com.fishmatrix.music.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 曲库类型。
 *
 * <p>枚举名与数据库 VARCHAR 中存的值一致，Jackson 默认按名称序列化，
 * 因此对外 JSON 与改造前完全相同，前端不需要改动。</p>
 */
public enum TrackScope implements IEnum<String> {
    PUBLIC,
    PRIVATE;

    @Override
    public String getValue() {
        return name();
    }

    public boolean isPublic() {
        return this == PUBLIC;
    }

    public boolean isPrivate() {
        return this == PRIVATE;
    }
}
