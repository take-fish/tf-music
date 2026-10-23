-- 公共曲库 / 个人私有曲库、分类、歌单、访问申请与播放记录。
-- 音频文件本身落本地卷，数据库只保存元数据与 storage_key。

CREATE TABLE music_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_music_category_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE music_track (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    artist VARCHAR(128) NOT NULL DEFAULT '',
    album VARCHAR(128) NOT NULL DEFAULT '',
    duration_sec INT NOT NULL DEFAULT 0,
    scope VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    owner_id BIGINT NULL,
    owner_nickname VARCHAR(32) NOT NULL DEFAULT '',
    category_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    share_token VARCHAR(64) NULL,
    source_filename VARCHAR(255) NOT NULL DEFAULT '',
    storage_key VARCHAR(512) NULL,
    content_type VARCHAR(64) NOT NULL DEFAULT 'application/octet-stream',
    file_size BIGINT NOT NULL DEFAULT 0,
    sha256 CHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_music_track_share_token (share_token),
    KEY idx_music_track_public (scope, status, category_id, updated_at),
    KEY idx_music_track_owner (owner_id, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE music_playlist (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_music_playlist_owner (owner_id, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE music_playlist_track (
    playlist_id BIGINT NOT NULL,
    track_id BIGINT NOT NULL,
    position_no INT NOT NULL DEFAULT 0,
    added_at DATETIME(3) NOT NULL,
    PRIMARY KEY (playlist_id, track_id),
    KEY idx_music_playlist_track_track (track_id),
    CONSTRAINT fk_music_playlist_track_playlist FOREIGN KEY (playlist_id)
        REFERENCES music_playlist (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 同一首曲目同一申请人只保留一条状态记录。
CREATE TABLE music_access_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    track_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    requester_nickname VARCHAR(32) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL,
    message VARCHAR(255) NOT NULL DEFAULT '',
    decision_message VARCHAR(255) NOT NULL DEFAULT '',
    created_at DATETIME(3) NOT NULL,
    decided_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_music_access_track_requester (track_id, requester_id),
    KEY idx_music_access_owner (owner_id, status, updated_at),
    KEY idx_music_access_requester (requester_id, status, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE music_playback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    track_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    position_sec INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_music_playback_user (user_id, created_at),
    KEY idx_music_playback_track (track_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
