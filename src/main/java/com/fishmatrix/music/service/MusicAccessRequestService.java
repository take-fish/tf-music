package com.fishmatrix.music.service;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.music.model.AccessRequestCreate;
import com.fishmatrix.music.model.AccessRequestView;
import com.fishmatrix.music.repository.AccessRequestRepository;
import com.fishmatrix.music.repository.TrackRepository;
import com.fishmatrix.music.repository.TrackRepository.TrackRecord;
import com.fishmatrix.music.util.Validation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MusicAccessRequestService {

    private static final Duration REJECT_COOLDOWN = Duration.ofHours(24);

    private final AccessRequestRepository repository;
    private final TrackRepository trackRepository;

    @Transactional
    public AccessRequestView request(long trackId, AccessRequestCreate create,
            CurrentUser viewer) {
        TrackRecord track = trackRepository.findById(trackId)
                .filter(TrackRecord::isPrivate)
                .orElseThrow(() -> ApiException.notFound("曲目不存在"));
        if (track.isOwnedBy(viewer.id())) {
            throw ApiException.badRequest("不能申请访问自己的曲目");
        }
        if (create == null || create.shareToken() == null || create.shareToken().isBlank()
                || !create.shareToken().equals(track.shareToken())) {
            throw ApiException.forbidden("请通过有效的分享链接发起申请");
        }
        String message = Validation.text(create.message(), 255, "申请说明", false);
        Instant now = Instant.now();
        Optional<AccessRequestView> existing =
                repository.findByTrackAndRequester(trackId, viewer.id());
        if (existing.isPresent()) {
            AccessRequestView current = existing.get();
            switch (current.status()) {
                case "APPROVED", "PENDING" -> {
                    return current;
                }
                case "REJECTED" -> {
                    if (current.decidedAt() != null
                            && current.decidedAt().plus(REJECT_COOLDOWN).isAfter(now)) {
                        throw ApiException.tooManyRequests(
                                "REQUEST_COOLDOWN", "申请被拒绝后 24 小时内不能重复申请");
                    }
                }
                default -> {
                    // CANCELLED / REVOKED 允许重新申请
                }
            }
        }
        long id = repository.upsertPending(trackId, track.ownerId(), viewer.id(),
                Validation.truncate(viewer.nickname(), 32), message, now);
        return repository.findById(id).orElseThrow();
    }

    public List<AccessRequestView> mine(CurrentUser viewer) {
        return repository.findByRequester(viewer.id());
    }

    public List<AccessRequestView> received(CurrentUser viewer, String status) {
        return repository.findByOwner(viewer.id(), status);
    }

    @Transactional
    public AccessRequestView approve(long id, CurrentUser viewer) {
        AccessRequestView request = requireOwnerPending(id, viewer);
        repository.decide(request.id(), "APPROVED", "", Instant.now());
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public AccessRequestView reject(long id, String reason, CurrentUser viewer) {
        AccessRequestView request = requireOwnerPending(id, viewer);
        repository.decide(request.id(), "REJECTED",
                Validation.text(reason, 255, "拒绝理由", false), Instant.now());
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public AccessRequestView revoke(long id, CurrentUser viewer) {
        AccessRequestView request = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("申请不存在"));
        if (request.ownerId() != viewer.id()) {
            throw ApiException.notFound("申请不存在");
        }
        if (!"APPROVED".equals(request.status())) {
            throw ApiException.conflict("INVALID_STATE", "只有已通过的申请才能撤销");
        }
        repository.decide(id, "REVOKED", "", Instant.now());
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public AccessRequestView cancel(long id, CurrentUser viewer) {
        AccessRequestView request = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("申请不存在"));
        if (request.requesterId() != viewer.id()) {
            throw ApiException.notFound("申请不存在");
        }
        if (!"PENDING".equals(request.status())) {
            throw ApiException.conflict("INVALID_STATE", "只有待审批的申请才能取消");
        }
        repository.decide(id, "CANCELLED", "", Instant.now());
        return repository.findById(id).orElseThrow();
    }

    private AccessRequestView requireOwnerPending(long id, CurrentUser viewer) {
        AccessRequestView request = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("申请不存在"));
        if (request.ownerId() != viewer.id()) {
            throw ApiException.notFound("申请不存在");
        }
        if (!"PENDING".equals(request.status())) {
            throw ApiException.conflict("INVALID_STATE", "申请已处理");
        }
        return request;
    }
}
