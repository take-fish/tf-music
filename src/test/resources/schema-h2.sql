CREATE TABLE IF NOT EXISTS music_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS music_track (
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
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (share_token)
);

CREATE INDEX IF NOT EXISTS idx_music_track_public
    ON music_track (scope, status, category_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_music_track_owner
    ON music_track (owner_id, updated_at);

CREATE TABLE IF NOT EXISTS music_playlist (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_music_playlist_owner
    ON music_playlist (owner_id, updated_at);

CREATE TABLE IF NOT EXISTS music_playlist_track (
    playlist_id BIGINT NOT NULL,
    track_id BIGINT NOT NULL,
    position_no INT NOT NULL DEFAULT 0,
    added_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (playlist_id, track_id),
    CONSTRAINT fk_music_playlist_track_playlist FOREIGN KEY (playlist_id)
        REFERENCES music_playlist (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_music_playlist_track_track
    ON music_playlist_track (track_id);

CREATE TABLE IF NOT EXISTS music_access_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    track_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    requester_nickname VARCHAR(32) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL,
    message VARCHAR(255) NOT NULL DEFAULT '',
    decision_message VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMP(3) NOT NULL,
    decided_at TIMESTAMP(3) NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (track_id, requester_id)
);

CREATE INDEX IF NOT EXISTS idx_music_access_owner
    ON music_access_request (owner_id, status, updated_at);

CREATE INDEX IF NOT EXISTS idx_music_access_requester
    ON music_access_request (requester_id, status, updated_at);

CREATE TABLE IF NOT EXISTS music_playback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    track_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    position_sec INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_music_playback_user
    ON music_playback (user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_music_playback_track
    ON music_playback (track_id, created_at);
