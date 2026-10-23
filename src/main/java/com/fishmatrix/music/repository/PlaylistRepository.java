package com.fishmatrix.music.repository;

import com.fishmatrix.music.model.Playlist;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class PlaylistRepository {

    private static final String SELECT = """
            SELECT p.id, p.name, p.description, p.updated_at,
                   (SELECT COUNT(*) FROM music_playlist_track i WHERE i.playlist_id = p.id)
                       AS track_count
              FROM music_playlist p
            """;

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public PlaylistRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.insert = new SimpleJdbcInsert(jdbc)
                .withTableName("music_playlist")
                .usingGeneratedKeyColumns("id");
    }

    public List<Playlist> findByOwner(long ownerId) {
        return jdbc.query(SELECT + " WHERE p.owner_id = ? ORDER BY p.updated_at DESC, p.id DESC",
                this::map, ownerId);
    }

    public Optional<Playlist> findById(long playlistId) {
        return jdbc.query(SELECT + " WHERE p.id = ?", this::map, playlistId)
                .stream().findFirst();
    }

    public boolean ownedBy(long playlistId, long ownerId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM music_playlist WHERE id = ? AND owner_id = ?",
                Long.class, playlistId, ownerId);
        return count != null && count > 0;
    }

    public List<Long> findTrackIds(long playlistId) {
        return jdbc.queryForList(
                "SELECT track_id FROM music_playlist_track WHERE playlist_id = ?"
                        + " ORDER BY position_no, added_at, track_id",
                Long.class, playlistId);
    }

    public long insert(long ownerId, String name, String description, Instant now) {
        return insert.executeAndReturnKey(java.util.Map.of(
                "owner_id", ownerId,
                "name", name,
                "description", description,
                "created_at", Timestamp.from(now),
                "updated_at", Timestamp.from(now))).longValue();
    }

    public void update(long playlistId, String name, String description, Instant now) {
        jdbc.update("UPDATE music_playlist SET name = ?, description = ?, updated_at = ?"
                + " WHERE id = ?", name, description, Timestamp.from(now), playlistId);
    }

    public boolean addTrack(long playlistId, long trackId, Instant now) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(position_no), 0) FROM music_playlist_track WHERE playlist_id = ?",
                Integer.class, playlistId);
        try {
            jdbc.update("INSERT INTO music_playlist_track"
                            + " (playlist_id, track_id, position_no, added_at) VALUES (?, ?, ?, ?)",
                    playlistId, trackId, (max == null ? 0 : max) + 1, Timestamp.from(now));
            touch(playlistId, now);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public boolean removeTrack(long playlistId, long trackId, Instant now) {
        boolean removed = jdbc.update(
                "DELETE FROM music_playlist_track WHERE playlist_id = ? AND track_id = ?",
                playlistId, trackId) > 0;
        if (removed) touch(playlistId, now);
        return removed;
    }

    public void delete(long playlistId) {
        jdbc.update("DELETE FROM music_playlist_track WHERE playlist_id = ?", playlistId);
        jdbc.update("DELETE FROM music_playlist WHERE id = ?", playlistId);
    }

    private void touch(long playlistId, Instant now) {
        jdbc.update("UPDATE music_playlist SET updated_at = ? WHERE id = ?",
                Timestamp.from(now), playlistId);
    }

    private Playlist map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Playlist(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("track_count"),
                rs.getTimestamp("updated_at").toInstant());
    }
}
