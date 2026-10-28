package com.fishmatrix.music.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.music.converter.ViewConverters;
import com.fishmatrix.music.domain.AccessRequestStatus;
import com.fishmatrix.music.entity.MusicAccessRequest;
import com.fishmatrix.music.entity.MusicTrack;
import com.fishmatrix.music.mapper.MusicAccessRequestMapper;
import com.fishmatrix.music.mapper.MusicTrackMapper;
import com.fishmatrix.music.model.AccessRequestCreate;
import com.fishmatrix.music.model.AccessRequestView;
import com.fishmatrix.music.util.Validation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MusicAccessRequestService {

    private static final Duration REJECT_COOLDOWN = Duration.ofHours(24);

    private final MusicAccessRequestMapper accessMapper;
    private final MusicTrackMapper trackMapper;

    @Transactional
    public AccessRequestView request(long trackId, AccessRequestCreate create,
            CurrentUser viewer) {
        MusicTrack track = trackMapper.selectViewById(trackId);
        if (track == null || !track.getScope().isPrivate()) {
            throw ApiException.notFound("曲目不存在");
        }
        if (Objects.equals(track.getOwnerId(), viewer.id())) {
            throw ApiException.badRequest("不能申请访问自己的曲目");
        }
        if (create == null || create.shareToken() == null || create.shareToken().isBlank()
                || !create.shareToken().equals(track.getShareToken())) {
            throw ApiException.forbidden("请通过有效的分享链接发起申请");
        }
        String message = Validation.text(create.message(), 255, "申请说明", false);
        Instant now = Instant.now();
        MusicAccessRequest existing =
                accessMapper.selectByTrackAndRequester(trackId, viewer.id());
        if (existing != null) {
            switch (existing.getStatus()) {
                case APPROVED, PENDING -> {
                    return ViewConverters.toAccessRequest(existing);
                }
                case REJECTED -> {
                    if (existing.getDecidedAt() != null
                            && existing.getDecidedAt().plus(REJECT_COOLDOWN).isAfter(now)) {
                        throw ApiException.tooManyRequests(
                                "REQUEST_COOLDOWN", "申请被拒绝后 24 小时内不能重复申请");
                    }
                }
                default -> {
                    // CANCELLED / REVOKED 允许重新申请
                }
            }
            // 重新申请：显式重置为待处理，并清掉上一次的审批结果。
            accessMapper.update(null, Wrappers.<MusicAccessRequest>lambdaUpdate()
                    .eq(MusicAccessRequest::getId, existing.getId())
                    .set(MusicAccessRequest::getOwnerId, track.getOwnerId())
                    .set(MusicAccessRequest::getRequesterNickname,
                            Validation.truncate(viewer.nickname(), 32))
                    .set(MusicAccessRequest::getStatus, AccessRequestStatus.PENDING)
                    .set(MusicAccessRequest::getMessage, message)
                    .set(MusicAccessRequest::getDecisionMessage, "")
                    .set(MusicAccessRequest::getCreatedAt, now)
                    .set(MusicAccessRequest::getDecidedAt, null)
                    .set(MusicAccessRequest::getUpdatedAt, now));
            return requireView(existing.getId());
        }

        MusicAccessRequest request = new MusicAccessRequest();
        request.setTrackId(trackId);
        request.setOwnerId(track.getOwnerId());
        request.setRequesterId(viewer.id());
        request.setRequesterNickname(Validation.truncate(viewer.nickname(), 32));
        request.setStatus(AccessRequestStatus.PENDING);
        request.setMessage(message);
        request.setDecisionMessage("");
        accessMapper.insert(request);
        return requireView(request.getId());
    }

    public List<AccessRequestView> mine(CurrentUser viewer) {
        return accessMapper.selectByRequester(viewer.id()).stream()
                .map(ViewConverters::toAccessRequest).toList();
    }

    public List<AccessRequestView> received(CurrentUser viewer, String status) {
        return accessMapper.selectByOwner(viewer.id(), parseStatus(status)).stream()
                .map(ViewConverters::toAccessRequest).toList();
    }

    @Transactional
    public AccessRequestView approve(long id, CurrentUser viewer) {
        requireOwnerPending(id, viewer);
        decide(id, AccessRequestStatus.APPROVED, "");
        return requireView(id);
    }

    @Transactional
    public AccessRequestView reject(long id, String reason, CurrentUser viewer) {
        requireOwnerPending(id, viewer);
        decide(id, AccessRequestStatus.REJECTED,
                Validation.text(reason, 255, "拒绝理由", false));
        return requireView(id);
    }

    @Transactional
    public AccessRequestView revoke(long id, CurrentUser viewer) {
        MusicAccessRequest request = requireOwned(id, viewer);
        if (!request.getStatus().isApproved()) {
            throw ApiException.conflict("INVALID_STATE", "只有已通过的申请才能撤销");
        }
        decide(id, AccessRequestStatus.REVOKED, "");
        return requireView(id);
    }

    @Transactional
    public AccessRequestView cancel(long id, CurrentUser viewer) {
        MusicAccessRequest request = requireById(id);
        if (!Objects.equals(request.getRequesterId(), viewer.id())) {
            throw ApiException.notFound("申请不存在");
        }
        if (!request.getStatus().isPending()) {
            throw ApiException.conflict("INVALID_STATE", "只有待审批的申请才能取消");
        }
        decide(id, AccessRequestStatus.CANCELLED, "");
        return requireView(id);
    }

    private void decide(long id, AccessRequestStatus status, String decisionMessage) {
        MusicAccessRequest patch = new MusicAccessRequest();
        patch.setId(id);
        patch.setStatus(status);
        patch.setDecisionMessage(decisionMessage);
        patch.setDecidedAt(Instant.now());
        accessMapper.updateById(patch);
    }

    private void requireOwnerPending(long id, CurrentUser viewer) {
        MusicAccessRequest request = requireOwned(id, viewer);
        if (!request.getStatus().isPending()) {
            throw ApiException.conflict("INVALID_STATE", "申请已处理");
        }
    }

    private MusicAccessRequest requireOwned(long id, CurrentUser viewer) {
        MusicAccessRequest request = requireById(id);
        if (!Objects.equals(request.getOwnerId(), viewer.id())) {
            throw ApiException.notFound("申请不存在");
        }
        return request;
    }

    private MusicAccessRequest requireById(long id) {
        MusicAccessRequest request = accessMapper.selectById(id);
        if (request == null) {
            throw ApiException.notFound("申请不存在");
        }
        return request;
    }

    private AccessRequestView requireView(long id) {
        MusicAccessRequest request = accessMapper.selectViewById(id);
        if (request == null) {
            throw ApiException.notFound("申请不存在");
        }
        return ViewConverters.toAccessRequest(request);
    }

    private static AccessRequestStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AccessRequestStatus.valueOf(status.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("申请状态不合法");
        }
    }
}
