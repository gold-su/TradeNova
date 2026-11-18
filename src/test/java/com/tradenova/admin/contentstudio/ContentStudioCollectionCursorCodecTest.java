package com.tradenova.admin.contentstudio;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentStudioCollectionCursorCodecTest {

    private final ContentStudioCollectionCursorCodec codec = new ContentStudioCollectionCursorCodec();

    @Test
    void roundTripsCollectionCursor() {
        var cursor = new ContentStudioCollectionCursorCodec.Cursor(
                LocalDate.of(2021, 1, 1), LocalDate.of(2025, 12, 31),
                17, LocalDate.of(2023, 6, 30));

        assertThat(codec.decode(codec.encode(cursor))).isEqualTo(cursor);
    }

    @Test
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> codec.decode("not-a-valid-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_STUDIO_COLLECTION_CURSOR_INVALID);
    }
}
