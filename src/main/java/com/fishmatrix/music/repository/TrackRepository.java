package com.fishmatrix.music.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class TrackRepository {

    private static final String SELECT_TRACK = """
            SELECT t.id, t.title, t.artist, t.album, t.duration_sec, t.scope, t.owner_id,
                   t.owner_nickname, t.category_id, c.name AS category_name, t.status,
                   t.share_token, t.storage_key, t.source_filename, t.content_type,
                   t.file_size, t.sha256, t.created_at, t.updated_at
              FROM music_track t
              LEFT JOIN music_category c ON c.id = t.category_id
            """;

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public TrackRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.insert = new SimpleJdbcInsert(jdbc)
                .withTableName("music_track")
                .usingGeneratedKeyColumns("id");
    }

    public List<TrackRecord> findPublic(Long categoryId, String keyword, int offset, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_TRACK)
                .append(" WHERE t.scope = 'PUBLIC' AND t.status = 'VISIBLE'");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, categoryId, keyword);
        sql.append(" ORDER BY t.updated_at DESC, t.id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), this::map, args.toArray());
    }

    public long countPublic(Long categoryId, String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM music_track t"
                        + " WHERE t.scope = 'PUBLIC' AND t.status = 'VISIBLE'");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, categoryId, keyword);
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    public List<TrackRecord> findOwned(long ownerId) {
        return jdbc.query(SELECT_TRACK + " WHERE t.scope = 'PRIVATE' AND t.owner_id = ?"
                + " ORDER BY t.updated_at DESC, t.id DESC", this::map, ownerId);
    }

    public List<TrackRecord> findShared(long viewerId) {
        return jdbc.query(SELECT_TRACK
                + " JOIN music_access_request r ON r.track_id = t.id"
                + " WHERE t.scope = 'PRIVATE' AND r.requester_id = ?"
                + " AND r.status = 'APPROVED' AND t.owner_id <> ?"
                + " ORDER BY t.updated_at DESC, t.id DESC",
                this::map, viewerId, viewerId);
    }

    public Optional<TrackRecord> findById(long trackId) {
        return jdbc.query(SELECT_TRACK + " WHERE t.id = ?", this::map, trackId)
                .stream().findFirst();
    }

    public Optional<TrackRecord> findByShareToken(String shareToken) {
        if (shareToken == null || shareToken.isBlank()) return Optional.empty();
        return jdbc.query(SELECT_TRACK + " WHERE t.share_token = ?", this::map, shareToken)
                .stream().findFirst();
    }

    public boolean hasApprovedAccess(long trackId, long viewerId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM music_access_request"
                        + " WHERE track_id = ? AND requester_id = ? AND status = 'APPROVED'",
                Long.class, trackId, viewerId);
        return count != null && count > 0;
    }

    public long insertTrack(String title, String artist, String album, int durationSec,
            String scope, Long ownerId, String ownerNickname, Long categoryId,
            String sourceFilename, String storageKey, String contentType, long fileSize,
            String sha256, Instant now) {
        Map<String, Object> params = new HashMap<>();
        params.put("title", title);
        params.put("artist", artist);
        params.put("album", album);
        params.put("duration_sec", durationSec);
        params.put("scope", scope);
        params.put("owner_id", ownerId);
        params.put("owner_nickname", ownerNickname);
        params.put("category_id", categoryId);
        params.put("status", "VISIBLE");
        params.put("source_filename", sourceFilename);
        params.put("storage_key", storageKey);
        params.put("content_type", contentType);
        params.put("file_size", fileSize);
        params.put("sha256", sha256);
        params.put("created_at", Timestamp.from(now));
        params.put("updated_at", Timestamp.from(now));
        return insert.executeAndReturnKey(params).longValue();
    }

    public void updateMetadata(long trackId, String title, String artist, String album,
            int durationSec, Long categoryId, String status, Instant now) {
        jdbc.update("UPDATE music_track SET title = ?, artist = ?, album = ?,"
                        + " duration_sec = ?, category_id = ?, status = ?, updated_at = ?"
                        + " WHERE id = ?",
                title, artist, album, durationSec, categoryId, status,
                Timestamp.from(now), trackId);
    }

    public void updateShareToken(long trackId, String shareToken, Instant now) {
        jdbc.update("UPDATE music_track SET share_token = ?, updated_at = ? WHERE id = ?",
                shareToken, Timestamp.from(now), trackId);
    }

    public void deleteTrack(long trackId) {
        jdbc.update("DELETE FROM music_playlist_track WHERE track_id = ?", trackId);
        jdbc.update("DELETE FROM music_access_request WHERE track_id = ?", trackId);
        jdbc.update("DELETE FROM music_playback WHERE track_id = ?", trackId);
        jdbc.update("DELETE FROM music_track WHERE id = ?", trackId);
    }

    private static void appendFilters(StringBuilder sql, List<Object> args,
            Long categoryId, String keyword) {
        if (categoryId != null) {
            sql.append(" AND t.category_id = ?");
            args.add(categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (LOWER(t.title) LIKE ? OR LOWER(t.artist) LIKE ?"
                    + " OR LOWER(t.album) LIKE ?)");
            String like = "%" + keyword.strip().toLowerCase() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
    }

    private TrackRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new TrackRecord(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("artist"),
                rs.getString("album"),
                rs.getInt("duration_sec"),
                rs.getString("scope"),
                rs.getObject("owner_id", Long.class),
                rs.getString("owner_nickname"),
                rs.getObject("category_id", Long.class),
                rs.getString("category_name"),
                rs.getString("status"),
                rs.getString("share_token"),
                rs.getString("storage_key"),
                rs.getString("source_filename"),
                rs.getString("content_type"),
                rs.getLong("file_size"),
                rs.getString("sha256"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    public record TrackRecord(
            long id,
            String title,
            String artist,
            String album,
            int durationSec,
            String scope,
            Long ownerId,
            String ownerNickname,
            Long categoryId,
            String categoryName,
            String status,
            String shareToken,
            String storageKey,
            String sourceFilename,
            String contentType,
            long fileSize,
            String sha256,
            Instant createdAt,
            Instant updatedAt) {

        public boolean isPublic() {
            return "PUBLIC".equals(scope);
        }

        public boolean isPrivate() {
            return "PRIVATE".equals(scope);
        }

        public boolean isVisible() {
            return "VISIBLE".equals(status);
        }

        public boolean isOwnedBy(long userId) {
            return ownerId != null && ownerId == userId;
        }
    }
}
