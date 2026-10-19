# tf-music

FishMatrix（摸鱼矩阵）音乐服务：歌单与播放记录 —— Spring Boot 3 + JDK 17。

- 端口：`8105`
- 健康检查：`GET /actuator/health`、`GET /api/v1/music/health`
- 一期骨架：固定 1 条构建流水线（`fishmatrix-api #128`）+ 3 个构建任务
- 播放记录保存在进程内（最多 100 条，重启即失，符合骨架定位）
- 本地音频由前端 `<audio>` 播放，不上传、不持久化

## API

| 方法/路径 | 说明 |
|---|---|
| `GET /api/v1/music/playlists` | 歌单（构建流水线）列表 |
| `GET /api/v1/music/playlists/{id}` | 歌单详情与曲目 |
| `POST /api/v1/music/playlists/{id}/records` | 记录 start/pause/resume/complete/seek |
| `GET /api/v1/music/records?limit=20` | 最近播放记录 |

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
