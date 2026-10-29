package com.fishmatrix.music.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EntityMappingTest {

    private static final Map<Class<?>, String> EXPECTED = Map.of(
            MusicCategory.class, "music_category",
            MusicTrack.class, "music_track",
            MusicPlaylist.class, "music_playlist",
            MusicPlaylistTrack.class, "music_playlist_track",
            MusicAccessRequest.class, "music_access_request",
            MusicPlayback.class, "music_playback");

    private static final Set<String> AUDIT_FIELDS = Set.of("createdAt", "updatedAt", "addedAt");

    @Test
    @DisplayName("实体与表名一一对应，且除复合主键外都有自增主键")
    void tablesAndKeys() {
        EXPECTED.forEach((type, expectedTable) -> {
            TableName tableName = type.getAnnotation(TableName.class);
            assertThat(tableName).as("%s 缺少 @TableName", type.getSimpleName()).isNotNull();
            assertThat(tableName.value()).isEqualTo(expectedTable);
        });

        for (Class<?> type : EXPECTED.keySet()) {
            if (type == MusicPlaylistTrack.class) {
                assertThat(hasAnnotatedId(type))
                        .as("%s 是复合主键，不应有 @TableId", type.getSimpleName())
                        .isFalse();
            } else {
                assertThat(hasAnnotatedId(type))
                        .as("%s 缺少 @TableId", type.getSimpleName())
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("联表带出的字段都标了 exist = false")
    void joinedFieldsAreNotColumns() throws NoSuchFieldException {
        assertThat(notAColumn(MusicTrack.class, "categoryName")).isTrue();
        assertThat(notAColumn(MusicPlaylist.class, "trackCount")).isTrue();
        assertThat(notAColumn(MusicAccessRequest.class, "trackTitle")).isTrue();
        assertThat(notAColumn(MusicAccessRequest.class, "ownerNickname")).isTrue();
        assertThat(notAColumn(MusicPlayback.class, "trackTitle")).isTrue();
    }

    @Test
    @DisplayName("@TableId 落在 id 字段且为自增")
    void tableIdOnIdFieldWithAutoType() throws NoSuchFieldException {
        for (Class<?> type : EXPECTED.keySet()) {
            if (type == MusicPlaylistTrack.class) {
                continue;
            }
            Field idField = type.getDeclaredField("id");
            TableId tableId = idField.getAnnotation(TableId.class);
            assertThat(tableId)
                    .as("%s 的 @TableId 应落在 id 字段", type.getSimpleName())
                    .isNotNull();
            assertThat(tableId.type()).as("%s 的主键应为 AUTO", type.getSimpleName())
                    .isEqualTo(IdType.AUTO);
        }
    }

    @Test
    @DisplayName("审计字段类型精确为 Instant 且标注了 fill")
    void auditFieldsUseInstantWithFill() {
        int total = 0;
        for (Class<?> type : EXPECTED.keySet()) {
            for (Field field : type.getDeclaredFields()) {
                if (!AUDIT_FIELDS.contains(field.getName())) {
                    continue;
                }
                total++;
                assertThat(field.getType())
                        .as("%s.%s 必须是 Instant", type.getSimpleName(), field.getName())
                        .isEqualTo(Instant.class);
                TableField tableField = field.getAnnotation(TableField.class);
                assertThat(tableField)
                        .as("%s.%s 缺少 @TableField", type.getSimpleName(), field.getName())
                        .isNotNull();
                assertThat(tableField.fill())
                        .as("%s.%s 的 fill 不能是 DEFAULT", type.getSimpleName(), field.getName())
                        .isNotEqualTo(FieldFill.DEFAULT);
            }
        }
        assertThat(total).as("审计字段总数应为 9").isEqualTo(9);
    }

    private static boolean notAColumn(Class<?> type, String fieldName) throws NoSuchFieldException {
        Field field = type.getDeclaredField(fieldName);
        TableField annotation = field.getAnnotation(TableField.class);
        return annotation != null && !annotation.exist();
    }

    private static boolean hasAnnotatedId(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(TableId.class)) {
                return true;
            }
        }
        return false;
    }
}
