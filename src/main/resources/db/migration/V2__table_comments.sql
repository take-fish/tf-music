-- tf-music 全表中文注释
-- 只修改表与字段注释，不改变类型、默认值、索引；列定义逐字取自 MySQL 8.4 的 SHOW CREATE TABLE。
-- 列注释统一用 ALGORITHM=INSTANT，若定义与生产不一致会直接报错，避免静默改结构。

ALTER TABLE `music_category` COMMENT = '音乐分类';

ALTER TABLE `music_category`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类主键，自增',
    MODIFY COLUMN `name` varchar(64) NOT NULL COMMENT '分类名称，唯一',
    MODIFY COLUMN `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值，越大越靠前',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '创建时间',
    ALGORITHM=INSTANT;

ALTER TABLE `music_track` COMMENT = '音乐曲目：公共库与私有库曲目的元数据';

ALTER TABLE `music_track`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '曲目主键，自增',
    MODIFY COLUMN `title` varchar(255) NOT NULL COMMENT '曲名',
    MODIFY COLUMN `artist` varchar(128) NOT NULL DEFAULT '' COMMENT '歌手',
    MODIFY COLUMN `album` varchar(128) NOT NULL DEFAULT '' COMMENT '专辑',
    MODIFY COLUMN `duration_sec` int NOT NULL DEFAULT '0' COMMENT '时长秒数',
    MODIFY COLUMN `scope` varchar(16) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见范围，PUBLIC 公共库 / PRIVATE 私有库',
    MODIFY COLUMN `owner_id` bigint DEFAULT NULL COMMENT '上传者用户 ID',
    MODIFY COLUMN `owner_nickname` varchar(32) NOT NULL DEFAULT '' COMMENT '上传者昵称快照',
    MODIFY COLUMN `category_id` bigint DEFAULT NULL COMMENT '分类 ID，关联 music_category.id',
    MODIFY COLUMN `status` varchar(16) NOT NULL DEFAULT 'VISIBLE' COMMENT '展示状态，VISIBLE 可见 / HIDDEN 已隐藏',
    MODIFY COLUMN `share_token` varchar(64) DEFAULT NULL COMMENT '私有曲目分享码，为空表示未分享',
    MODIFY COLUMN `source_filename` varchar(255) NOT NULL DEFAULT '' COMMENT '上传时的原始文件名',
    MODIFY COLUMN `storage_key` varchar(512) DEFAULT NULL COMMENT '音频文件存储键',
    MODIFY COLUMN `content_type` varchar(64) NOT NULL DEFAULT 'application/octet-stream' COMMENT '音频 MIME 类型',
    MODIFY COLUMN `file_size` bigint NOT NULL DEFAULT '0' COMMENT '音频文件字节数',
    MODIFY COLUMN `sha256` char(64) DEFAULT NULL COMMENT '音频文件 SHA-256，用于去重与校验',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '入库时间',
    MODIFY COLUMN `updated_at` datetime(3) NOT NULL COMMENT '最后更新时间',
    ALGORITHM=INSTANT;

ALTER TABLE `music_playlist` COMMENT = '用户歌单';

ALTER TABLE `music_playlist`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '歌单主键，自增',
    MODIFY COLUMN `owner_id` bigint NOT NULL COMMENT '歌单所有者用户 ID',
    MODIFY COLUMN `name` varchar(64) NOT NULL COMMENT '歌单名称',
    MODIFY COLUMN `description` varchar(255) NOT NULL DEFAULT '' COMMENT '歌单简介',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '创建时间',
    MODIFY COLUMN `updated_at` datetime(3) NOT NULL COMMENT '最后更新时间',
    ALGORITHM=INSTANT;

ALTER TABLE `music_playlist_track` COMMENT = '歌单与曲目的关联';

ALTER TABLE `music_playlist_track`
    MODIFY COLUMN `playlist_id` bigint NOT NULL COMMENT '歌单 ID',
    MODIFY COLUMN `track_id` bigint NOT NULL COMMENT '曲目 ID',
    MODIFY COLUMN `position_no` int NOT NULL DEFAULT '0' COMMENT '歌单内顺序号',
    MODIFY COLUMN `added_at` datetime(3) NOT NULL COMMENT '加入歌单时间',
    ALGORITHM=INSTANT;

ALTER TABLE `music_access_request` COMMENT = '私有曲目访问申请：申请、审批与撤销记录';

ALTER TABLE `music_access_request`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请主键，自增',
    MODIFY COLUMN `track_id` bigint NOT NULL COMMENT '申请访问的曲目 ID',
    MODIFY COLUMN `owner_id` bigint NOT NULL COMMENT '曲目所有者用户 ID',
    MODIFY COLUMN `requester_id` bigint NOT NULL COMMENT '申请人用户 ID',
    MODIFY COLUMN `requester_nickname` varchar(32) NOT NULL DEFAULT '' COMMENT '申请人昵称快照',
    MODIFY COLUMN `status` varchar(16) NOT NULL COMMENT '申请状态，PENDING 待处理 / APPROVED 已通过 / REJECTED 已拒绝 / CANCELLED 已取消 / REVOKED 已撤销',
    MODIFY COLUMN `message` varchar(255) NOT NULL DEFAULT '' COMMENT '申请留言',
    MODIFY COLUMN `decision_message` varchar(255) NOT NULL DEFAULT '' COMMENT '审批回复',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '申请时间',
    MODIFY COLUMN `decided_at` datetime(3) DEFAULT NULL COMMENT '审批时间，未处理为空',
    MODIFY COLUMN `updated_at` datetime(3) NOT NULL COMMENT '最后更新时间',
    ALGORITHM=INSTANT;

ALTER TABLE `music_playback` COMMENT = '播放流水：记录播放、暂停、续播、拖动与播完动作';

ALTER TABLE `music_playback`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水主键，自增',
    MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '播放用户 ID',
    MODIFY COLUMN `track_id` bigint NOT NULL COMMENT '曲目 ID',
    MODIFY COLUMN `action` varchar(16) NOT NULL COMMENT '播放动作，start 开始 / pause 暂停 / resume 续播 / complete 播完 / seek 拖动',
    MODIFY COLUMN `position_sec` int NOT NULL DEFAULT '0' COMMENT '动作发生时的播放位置秒数',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '记录时间',
    ALGORITHM=INSTANT;
