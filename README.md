# tf-music

FishMatrix（摸鱼矩阵）音乐服务：公共曲库、个人曲库、歌单、分类、访问申请、流式播放与播放记录 —— Spring Boot 3 + JDK 17。

- 端口：`8105`
- 健康检查：`GET /actuator/health`、`GET /api/v1/music/health`
- 存储：MySQL `fm_music`（`music_track / music_playlist / music_playlist_track / music_category / music_access_request / music_playback`）
- 文件：音频落本地命名卷（`fm.storage`），库表只存 `storage_key`、大小、MIME 与 SHA-256
- 鉴权：统一使用用户中台身份（`Authorization: Bearer` 或网关签名身份头），权限码来自用户中台
- 支持导入 mp3 / m4a / aac / ogg / wav / flac / webm（≤50 MiB）
- 流式播放支持单段 HTTP `Range`，浏览器拖动进度条只取需要的字节

## 功能分级

| 能力 | 谁可以 | 权限码 |
|---|---|---|
| 浏览公共曲库 | 所有登录用户 | — |
| 上传公共曲目 | 所有登录用户 | `music.public.upload` |
| 上传到个人曲库 | 所有登录用户 | `music.private.manage` |
| 编辑/删除自己的曲目 | 上传者本人 | — |
| 编辑/删除任意曲目（含公共） | 管理员或被授权角色 | `music.content.manage.any` |
| 维护分类 | 管理员或被授权角色 | `music.category.manage` |
| 个人歌单 | 歌单所有者 | `music.private.manage` |
| 访问他人私有曲目 | 申请并获 owner 同意 | — |

判定规则：

- `scope=PUBLIC` 的曲目所有登录用户可听；`scope=PRIVATE` 的曲目只有 `owner_id` 本人可听可管。
- 私有曲目对非 owner 一律返回 **404**（不暴露是否存在）；`status=HIDDEN` 的曲目仅本人与 `music.content.manage.any` 可见。
- 列表项由服务端计算 `mine / canEdit / canDelete`，前端不自行推断。
- 删除只允许 owner 或具备 `music.content.manage.any`；分类变更需要 `music.category.manage`。
- 播放记录与歌单均按 `user_id` 隔离，个人歌单跨设备同步、仅本人可见。

## 访问申请

分享只用于“发现并发起申请”，**不等于授权**：

- `POST /api/v1/music/tracks/{id}/share` 仅私有曲目的 owner 可调用，生成/返回 `share_token`；
- `GET /api/v1/music/tracks/shared/{shareToken}` 只返回摘要信息，不返回音频流；
- 被分享者用 `POST /api/v1/music/tracks/{id}/access-requests`（body `{"shareToken":"...","message":"..."}`）发起申请；
- owner 通过 `access-requests/received` 查看，`approve` 后可通过 `/stream` 播放；`revoke` 收回。

状态机：`PENDING → APPROVED / REJECTED / CANCELLED`，`APPROVED → REVOKED`。被拒绝后 24 小时内再次申请返回 429 `REQUEST_COOLDOWN`；`CANCELLED` / `REVOKED` 可以重新申请。

## API 一览

全部接口需要登录；仅 `/health`（含 `/actuator/health`）公开。

| 方法/路径 | 说明 |
|---|---|
| `GET /api/v1/music/tracks?scope=public&categoryId=&q=&page=&size=` | 公共曲库；`scope=private` 等价于“我的曲目” |
| `GET /api/v1/music/tracks/mine` | 我的曲目（含私有） |
| `GET /api/v1/music/tracks/shared` | 我有权访问的他人私有曲目 |
| `GET /api/v1/music/tracks/shared/{shareToken}` | 分享链接预览（摘要） |
| `GET /api/v1/music/tracks/{id}` | 曲目详情 |
| `POST /api/v1/music/tracks/import` | multipart 导入：`file`、`scope`、`title`、`artist`、`album`、`durationSec`、`categoryId` |
| `PATCH /api/v1/music/tracks/{id}` | 修改标题/歌手/专辑/时长/分类/可见状态 |
| `DELETE /api/v1/music/tracks/{id}` | 删除曲目（owner 或 `music.content.manage.any`） |
| `POST /api/v1/music/tracks/{id}/share` | 生成私有曲目分享链接 |
| `GET /api/v1/music/tracks/{id}/stream` | 音频流，支持单段 `Range`，返回 200 或 206 |
| `POST /api/v1/music/tracks/{id}/access-requests` | 发起访问申请 |
| `GET /api/v1/music/access-requests/mine` | 我发出的申请 |
| `GET /api/v1/music/access-requests/received?status=` | 我收到的申请 |
| `POST /api/v1/music/access-requests/{id}/{approve,reject,revoke,cancel}` | 同意/拒绝/收回/撤销 |
| `GET/POST /api/v1/music/categories`、`PUT/DELETE /categories/{id}` | 分类；写入需 `music.category.manage` |
| `GET/POST /api/v1/music/playlists`、`GET/PUT/DELETE /playlists/{id}` | 个人歌单 |
| `POST /DELETE /api/v1/music/playlists/{id}/tracks/{trackId}` | 歌单增删曲目 |
| `POST /api/v1/music/tracks/{id}/records` | 记录 start/pause/resume/complete/seek（body `action`、`positionSec`） |
| `GET /api/v1/music/records?limit=20` | 我的最近播放记录 |

完整契约见 `api/openapi.yaml`。

## 配置

```yaml
fm:
  auth:
    enabled: true
    user-service-url: ${TF_USER_URL:http://127.0.0.1:8101}
    identity-secret: ${FM_IDENTITY_SECRET:}
    allow-bearer-fallback: true
  storage:
    enabled: true
    base-dir: ${FM_STORAGE_BASE_DIR:/app/data/files}
    max-file-bytes: 52428800
  messaging:
    enabled: false
  rpc:
    enabled: false
```

## 数据库初始化

- 表结构由 Flyway `db/migration/V1__baseline.sql` 创建，由独立任务 `db-migrate-music`
  使用迁移账号执行；业务容器固定 `SPRING_FLYWAY_ENABLED=false`、`SQL_INIT_MODE=never`。
- 迁移入口：`com.fishmatrix.music.migration.DatabaseMigrator`。
- 运行时账号只授 DML；迁移账号只授本库 DDL。
- H2 单元测试使用 `src/test/resources/schema-h2.sql`，不依赖 MySQL。

## 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` | `10.28.25.234` / `3306` | MySQL |
| `MYSQL_APP_USER` / `MYSQL_APP_PASSWORD` | `fm_music_app` / 空 | 运行时账号（仅 DML） |
| `NACOS_ADDR` / `NACOS_USERNAME` / `NACOS_PASSWORD` | — | 服务注册 |
| `FM_IDENTITY_SECRET` | 空 | 网关身份签名密钥，须与网关一致 |
| `TF_USER_URL` | `http://127.0.0.1:8101` | 用户中台内网地址（Bearer 回退 introspection） |
| `FM_STORAGE_ENABLED` / `FM_STORAGE_BASE_DIR` | `true` / `/app/data/files` | 文件存储与命名卷挂载点 |
| `MUSIC_MAX_UPLOAD_BYTES` | `52428800` | 单曲上传上限 |
| `SQL_INIT_MODE` | `never` | 业务容器不执行 SQL 初始化 |
| `SPRING_FLYWAY_ENABLED` | `false` | 业务容器不执行迁移，由 `db-migrate-music` 负责 |

## 本地开发

```bash
mvn spring-boot:run    # 启动开发服务
mvn -B test            # 运行测试（不依赖 MySQL/Redis/Nacos）
```

## 构建

```bash
mvn -B -DskipTests package   # 产物 target/tf-music.jar
java -jar target/tf-music.jar
```

## 分支模型

- `master`：保护分支，只接受 PR（CI 必须通过，squash 合并），合并后自动发版
- `test`：联调分支，开发分支直接合入（不走 PR），可直接推送
- 开发流：`feature/*` 开发 → 直接合入 `test` 联调 → 验证 OK 后从开发分支开 PR 合 `master` → 自动发版
