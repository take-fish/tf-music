package com.fishmatrix.music.repository;

import com.fishmatrix.music.model.PlaybackRecord;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class PlaybackRepository {

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public PlaybackRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.insert = new SimpleJdbcInsert(jdbc)
                .withTableName("music_playback")
                .usingGeneratedKeyColumns("id");
    }

    public long insert(long userId, long trackId, String action, int positionSec, Instant now) {
        return insert.executeAndReturnKey(java.util.Map.of(
                "user_id", userId,
                "track_id", trackId,
                "action", action,
                "position_sec", positionSec,
                "created_at", Timestamp.from(now))).longValue();
    }

    public List<PlaybackRecord> findByUser(long userId, int limit) {
        return jdbc.query("SELECT p.id, p.track_id, t.title AS track_title, p.action,"
                        + " p.position_sec, p.created_at FROM music_playback p"
                        + " LEFT JOIN music_track t ON t.id = p.track_id"
                        + " WHERE p.user_id = ? ORDER BY p.id DESC LIMIT ?",
                (rs, rowNum) -> new PlaybackRecord(
                        rs.getLong("id"),
                        rs.getLong("track_id"),
                        rs.getString("track_title"),
                        rs.getString("action"),
                        rs.getInt("position_sec"),
                        rs.getTimestamp("created_at").toInstant().toEpochMilli()),
                userId, limit);
    }
}
