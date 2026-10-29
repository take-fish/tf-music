package com.fishmatrix.music.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.music.domain.AccessRequestStatus;
import com.fishmatrix.music.domain.TrackScope;
import com.fishmatrix.music.error.NotFoundException;
import com.fishmatrix.music.model.AccessRequestCreate;
import com.fishmatrix.music.model.AccessRequestView;
import com.fishmatrix.music.model.Category;
import com.fishmatrix.music.model.CategoryRequest;
import com.fishmatrix.music.model.Playlist;
import com.fishmatrix.music.model.PlaylistDetail;
import com.fishmatrix.music.model.PlaylistRequest;
import com.fishmatrix.music.model.RecordRequest;
import com.fishmatrix.music.model.TrackPage;
import com.fishmatrix.music.model.TrackStream;
import com.fishmatrix.music.model.TrackSummary;
import com.fishmatrix.music.model.TrackUpdateRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MusicLibraryIntegrationTest {

    @Autowired
    private MusicLibraryService libraryService;

    @Autowired
    private MusicAccessRequestService accessRequestService;

    @Autowired
    private PlaylistService playlistService;

    @Autowired
    private MusicCategoryService categoryService;

    @Autowired
    private PlaybackService playbackService;

    private final CurrentUser owner = user(21, "歌主",
            "music.public.upload", "music.private.manage");
    private final CurrentUser stranger = user(22, "路人");
    private final CurrentUser admin = user(23, "管理员",
            "music.public.upload", "music.private.manage",
            "music.content.manage.any", "music.category.manage");

    @Test
    void publicUploadRequiresPermission() {
        assertThatThrownBy(() -> importTrack("public", stranger))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status().value())
                .isEqualTo(403);

        TrackSummary track = importTrack("public", owner);
        assertThat(track.scope()).isEqualTo(TrackScope.PUBLIC);
        assertThat(track.ownerId()).isEqualTo(owner.id());
        assertThat(track.contentType()).isEqualTo("audio/mpeg");
        assertThat(track.mine()).isTrue();
    }

    @Test
    void unsupportedAudioTypeIsRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "book.txt", "text/plain", "not audio".getBytes());
        assertThatThrownBy(() -> libraryService.importTrack("private", "标题", "", "",
                0, null, file, owner))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("音频");
    }

    @Test
    void privateTrackIsOnlyReadableByOwnerAndApprovedUsers() {
        TrackSummary track = importTrack("private", owner);

        assertThat(libraryService.getTrack(track.id(), owner).title()).isEqualTo(track.title());
        assertThatThrownBy(() -> libraryService.getTrack(track.id(), stranger))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> libraryService.getTrack(track.id(), admin))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> libraryService.stream(track.id(), stranger))
                .isInstanceOf(NotFoundException.class);
        assertThat(libraryService.listMine(owner))
                .extracting(TrackSummary::id).containsExactly(track.id());
        assertThat(libraryService.listMine(stranger)).isEmpty();
    }

    @Test
    void publicLibraryPagesAndFiltersByKeyword() {
        importTrack("public", owner);
        importTrack("public", owner);
        importTrack("public", owner);

        TrackPage firstPage = libraryService.listPublic(null, null, 0, 2, stranger);
        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.tracks()).hasSize(2);
        assertThat(firstPage.page()).isZero();
        assertThat(firstPage.size()).isEqualTo(2);

        TrackPage secondPage = libraryService.listPublic(null, null, 1, 2, stranger);
        assertThat(secondPage.tracks()).hasSize(1);

        assertThat(libraryService.listPublic(null, "测试", 0, 20, stranger).total()).isEqualTo(3);
        assertThat(libraryService.listPublic(null, "不存在的关键词", 0, 20, stranger).total())
                .isZero();
    }

    @Test
    void shareTokenOnlyAllowsApplyingUntilOwnerApproves() {
        TrackSummary track = importTrack("private", owner);
        String token = libraryService.share(track.id(), owner).shareToken();

        assertThat(libraryService.previewByShareToken(token, stranger).id())
                .isEqualTo(track.id());
        assertThatThrownBy(() -> accessRequestService.request(
                track.id(), new AccessRequestCreate("wrong", "想听"), stranger))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status().value())
                .isEqualTo(403);

        AccessRequestView request = accessRequestService.request(
                track.id(), new AccessRequestCreate(token, "想听"), stranger);
        assertThat(request.status()).isEqualTo(AccessRequestStatus.PENDING);
        assertThatThrownBy(() -> libraryService.stream(track.id(), stranger))
                .isInstanceOf(NotFoundException.class);

        accessRequestService.approve(request.id(), owner);
        TrackStream stream = libraryService.stream(track.id(), stranger);
        assertThat(stream.contentType()).isEqualTo("audio/mpeg");
        assertThat(stream.resource().exists()).isTrue();
        assertThat(libraryService.listShared(stranger))
                .extracting(TrackSummary::id).containsExactly(track.id());

        accessRequestService.revoke(request.id(), owner);
        assertThatThrownBy(() -> libraryService.getTrack(track.id(), stranger))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void onlyOwnerOrManagerCanModifyPublicTracks() {
        TrackSummary track = importTrack("public", owner);
        TrackUpdateRequest rename = new TrackUpdateRequest(
                "新歌名", null, null, null, null, null);

        assertThat(libraryService.update(track.id(), rename, owner).title())
                .isEqualTo("新歌名");
        assertThatThrownBy(() -> libraryService.update(track.id(), rename, stranger))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status().value())
                .isEqualTo(403);
        assertThat(libraryService.update(track.id(), rename, admin).title())
                .isEqualTo("新歌名");

        libraryService.delete(track.id(), admin);
        assertThatThrownBy(() -> libraryService.getTrack(track.id(), owner))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void privateTrackMetadataIsOwnerOnly() {
        TrackSummary track = importTrack("private", owner);
        TrackUpdateRequest rename = new TrackUpdateRequest(
                "改名", null, null, null, null, null);

        assertThat(libraryService.update(track.id(), rename, owner).title()).isEqualTo("改名");
        assertThatThrownBy(() -> libraryService.update(track.id(), rename, admin))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> libraryService.delete(track.id(), stranger))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void categoriesRequireManagerPermission() {
        Category category = categoryService.create(new CategoryRequest("轻音乐", 3));
        TrackSummary track = libraryService.importTrack("public", "分类曲", "歌手", "",
                120, category.id(), sampleFile(), admin);
        assertThat(track.categoryName()).isEqualTo("轻音乐");

        assertThatThrownBy(() -> libraryService.importTrack("public", "越权分类", "歌手", "",
                120, category.id(), sampleFile(), owner))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status().value())
                .isEqualTo(403);
    }

    @Test
    void playlistsAreOwnerScopedAndOnlyContainReadableTracks() {
        CurrentUser other = user(24, "别人", "music.private.manage");
        TrackSummary mine = importTrack("private", owner);
        TrackSummary others = importTrack("private", other);

        Playlist playlist = playlistService.create(new PlaylistRequest("夜跑", "备注"), owner);
        playlistService.addTrack(playlist.id(), mine.id(), owner);
        playlistService.addTrack(playlist.id(), mine.id(), owner);
        assertThatThrownBy(() -> playlistService.addTrack(playlist.id(), others.id(), owner))
                .isInstanceOf(NotFoundException.class);

        PlaylistDetail detail = playlistService.get(playlist.id(), owner);
        assertThat(detail.tracks()).extracting(TrackSummary::id).containsExactly(mine.id());
        assertThat(detail.playlist().trackCount()).isEqualTo(1);

        assertThat(playlistService.list(stranger)).isEmpty();
        assertThatThrownBy(() -> playlistService.get(playlist.id(), stranger))
                .isInstanceOf(NotFoundException.class);

        playlistService.removeTrack(playlist.id(), mine.id(), owner);
        assertThat(playlistService.get(playlist.id(), owner).tracks()).isEmpty();
    }

    @Test
    void playbackRequiresReadableTrackAndIsPersisted() {
        TrackSummary track = importTrack("private", owner);

        assertThatThrownBy(() -> playbackService.record(
                track.id(), new RecordRequest("start", 0), stranger))
                .isInstanceOf(NotFoundException.class);

        playbackService.record(track.id(), new RecordRequest("start", 0), owner);
        playbackService.record(track.id(), new RecordRequest("complete", 120), owner);
        assertThat(playbackService.records(owner, 10))
                .extracting(record -> record.action())
                .containsExactly("complete", "start");
        assertThat(playbackService.records(stranger, 10)).isEmpty();
    }

    private TrackSummary importTrack(String scope, CurrentUser who) {
        return libraryService.importTrack(scope, "测试曲目", "歌手", "专辑", 180, null,
                sampleFile(), who);
    }

    private MockMultipartFile sampleFile() {
        return new MockMultipartFile("file", "song.mp3", "audio/mpeg",
                "fake-mp3-bytes".getBytes(StandardCharsets.UTF_8));
    }

    private static CurrentUser user(long id, String nickname, String... permissions) {
        return new CurrentUser(id, "u" + id, nickname, "USER",
                List.of("USER"), Set.of(permissions));
    }
}
