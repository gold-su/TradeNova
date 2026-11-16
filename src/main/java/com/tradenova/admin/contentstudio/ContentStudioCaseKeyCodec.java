package com.tradenova.admin.contentstudio;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
class ContentStudioCaseKeyCodec {

    private static final byte VERSION = 2;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int DESCRIPTOR_BYTES = 24;
    private static final int CASE_KEY_BYTES = 1 + IV_BYTES + DESCRIPTOR_BYTES + TAG_BITS / 8;

    private final SecretKey key;
    private final SecureRandom secureRandom;

    @Autowired
    ContentStudioCaseKeyCodec(
            @Value("${app.content-studio.case-key-secret}") String encodedKey
    ) {
        this(encodedKey, new SecureRandom());
    }

    ContentStudioCaseKeyCodec(String encodedKey, SecureRandom secureRandom) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Content studio case key secret must be Base64 encoded", exception);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException("Content studio case key secret must decode to exactly 32 bytes");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = secureRandom;
    }

    String encode(CaseDescriptor descriptor) {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);

        ByteBuffer plaintext = ByteBuffer.allocate(DESCRIPTOR_BYTES);
        plaintext.putLong(descriptor.symbolId());
        plaintext.putLong(descriptor.startCandleId());
        plaintext.putInt(descriptor.analysisBars());
        plaintext.putInt(descriptor.futureBars());

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(new byte[]{VERSION});
            byte[] ciphertext = cipher.doFinal(plaintext.array());

            ByteBuffer token = ByteBuffer.allocate(CASE_KEY_BYTES);
            token.put(VERSION);
            token.put(iv);
            token.put(ciphertext);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(token.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to create content studio case key", exception);
        }
    }

    CaseDescriptor decode(String caseKey) {
        try {
            byte[] token = Base64.getUrlDecoder().decode(caseKey);
            if (token.length != CASE_KEY_BYTES) {
                throw invalidCaseKey();
            }

            ByteBuffer buffer = ByteBuffer.wrap(token);
            byte version = buffer.get();
            if (version != VERSION) {
                throw invalidCaseKey();
            }

            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(new byte[]{version});
            byte[] plaintext = cipher.doFinal(ciphertext);
            CaseDescriptor descriptor = readDescriptor(plaintext);
            validate(descriptor);
            return descriptor;
        } catch (CustomException exception) {
            throw exception;
        } catch (AEADBadTagException | IllegalArgumentException exception) {
            throw invalidCaseKey();
        } catch (GeneralSecurityException exception) {
            throw invalidCaseKey();
        }
    }

    private CaseDescriptor readDescriptor(byte[] plaintext) {
        if (plaintext.length != DESCRIPTOR_BYTES) {
            throw invalidCaseKey();
        }
        ByteBuffer buffer = ByteBuffer.wrap(plaintext);
        return new CaseDescriptor(buffer.getLong(), buffer.getLong(), buffer.getInt(), buffer.getInt());
    }

    private void validate(CaseDescriptor descriptor) {
        if (descriptor.symbolId() <= 0
                || descriptor.startCandleId() <= 0
                || descriptor.analysisBars() < 20
                || descriptor.analysisBars() > 240
                || descriptor.futureBars() < 1
                || descriptor.futureBars() > 120) {
            throw invalidCaseKey();
        }
    }

    private CustomException invalidCaseKey() {
        return new CustomException(ErrorCode.CONTENT_STUDIO_CASE_INVALID);
    }

    record CaseDescriptor(long symbolId, long startCandleId, int analysisBars, int futureBars) {
    }
}
