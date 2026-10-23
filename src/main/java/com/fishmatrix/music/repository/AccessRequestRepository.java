package com.fishmatrix.music.repository;

import com.fishmatrix.music.model.AccessRequestView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class AccessRequestRepository {

    private static final String SELECT = """
            SELECT r.id, r.track_id, t.title AS track_title, r.owner_id, r.requester_id,
                   t.owner_nickname, r.requester_nickname, r.status, r.message,
                   r.decision_message, r.created_at, r.decided_at
              FROM music_access_request r
              JOIN music_track t ON t.id = r.track_id
            """;

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public AccessRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.insert = new SimpleJdbcInsert(jdbc)
                .withTableName("music_access_request")
                .usingGeneratedKeyColumns("id");
    }

    public Optional<AccessRequestView> findByTrackAndRequester(long trackId, long requesterId) {
        return jdbc.query(SELECT + " WHERE r.track_id = ? AND r.requester_id = ?",
                this::map, trackId, requesterId).stream().findFirst();
    }

    public Optional<AccessRequestView> findById(long id) {
        return jdbc.query(SELECT + " WHERE r.id = ?", this::map, id).stream().findFirst();
    }

    public List<AccessRequestView> findByRequester(long requesterId) {
        return jdbc.query(SELECT + " WHERE r.requester_id = ?"
                + " ORDER BY r.updated_at DESC, r.id DESC", this::map, requesterId);
    }

    public List<AccessRequestView> findByOwner(long ownerId, String status) {
        if (status == null || status.isBlank()) {
            return jdbc.query(SELECT + " WHERE r.owner_id = ?"
                    + " ORDER BY r.updated_at DESC, r.id DESC", this::map, ownerId);
        }
        return jdbc.query(SELECT + " WHERE r.owner_id = ? AND r.status = ?"
                + " ORDER BY r.updated_at DESC, r.id DESC", this::map, ownerId, status);
    }

    public long upsertPending(long trackId, long ownerId, long requesterId,
            String requesterNickname, String message, Instant now) {
        Optional<AccessRequestView> existing = findByTrackAndRequester(trackId, requesterId);
        if (existing.isPresent()) {
            jdbc.update("UPDATE music_access_request SET owner_id = ?, requester_nickname = ?,"
                            + " status = 'PENDING', message = ?, decision_message = '',"
                            + " created_at = ?, decided_at = NULL, updated_at = ? WHERE id = ?",
                    ownerId, requesterNickname, message, Timestamp.from(now),
                    Timestamp.from(now), existing.get().id());
            return existing.get().id();
        }
        return insert.executeAndReturnKey(java.util.Map.of(
                "track_id", trackId,
                "owner_id", ownerId,
                "requester_id", requesterId,
                "requester_nickname", requesterNickname,
                "status", "PENDING",
                "message", message,
                "decision_message", "",
                "created_at", Timestamp.from(now),
                "updated_at", Timestamp.from(now))).longValue();
    }

    public void decide(long id, String status, String decisionMessage, Instant now) {
        jdbc.update("UPDATE music_access_request SET status = ?, decision_message = ?,"
                        + " decided_at = ?, updated_at = ? WHERE id = ?",
                status, decisionMessage, Timestamp.from(now), Timestamp.from(now), id);
    }

    private AccessRequestView map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp decidedAt = rs.getTimestamp("decided_at");
        return new AccessRequestView(
                rs.getLong("id"),
                rs.getLong("track_id"),
                rs.getString("track_title"),
                rs.getLong("owner_id"),
                rs.getString("owner_nickname"),
                rs.getLong("requester_id"),
                rs.getString("requester_nickname"),
                rs.getString("status"),
                rs.getString("message"),
                rs.getString("decision_message"),
                rs.getTimestamp("created_at").toInstant(),
                decidedAt == null ? null : decidedAt.toInstant());
    }
}
