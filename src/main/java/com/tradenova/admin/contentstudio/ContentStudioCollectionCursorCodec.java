package com.tradenova.admin.contentstudio;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.Base64;

@Component
class ContentStudioCollectionCursorCodec {

    private static final byte VERSION = 1;
    private static final int TOKEN_BYTES = 1 + Long.BYTES * 3 + Integer.BYTES;

    String encode(Cursor cursor) {
        ByteBuffer buffer = ByteBuffer.allocate(TOKEN_BYTES);
        buffer.put(VERSION);
        buffer.putLong(cursor.fromDate().toEpochDay());
        buffer.putLong(cursor.toDate().toEpochDay());
        buffer.putInt(cursor.symbolIndex());
        buffer.putLong(cursor.nextEndDate().toEpochDay());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    Cursor decode(String token) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(token);
            if (bytes.length != TOKEN_BYTES) {
                throw invalid();
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            if (buffer.get() != VERSION) {
                throw invalid();
            }
            return new Cursor(
                    LocalDate.ofEpochDay(buffer.getLong()),
                    LocalDate.ofEpochDay(buffer.getLong()),
                    buffer.getInt(),
                    LocalDate.ofEpochDay(buffer.getLong())
            );
        } catch (CustomException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private CustomException invalid() {
        return new CustomException(ErrorCode.CONTENT_STUDIO_COLLECTION_CURSOR_INVALID);
    }

    record Cursor(LocalDate fromDate, LocalDate toDate, int symbolIndex, LocalDate nextEndDate) {
    }
}
