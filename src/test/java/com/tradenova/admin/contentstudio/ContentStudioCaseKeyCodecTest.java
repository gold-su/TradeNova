package com.tradenova.admin.contentstudio;

import com.tradenova.common.exception.CustomException;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentStudioCaseKeyCodecTest {

    private static final String SECRET = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    @Test
    void roundTripsAcrossCodecInstancesWhenSecretIsStable() {
        ContentStudioCaseKeyCodec.CaseDescriptor descriptor =
                new ContentStudioCaseKeyCodec.CaseDescriptor(41L, 902L, 80, 40);

        String caseKey = new ContentStudioCaseKeyCodec(SECRET, new SecureRandom()).encode(descriptor);

        assertThat(new ContentStudioCaseKeyCodec(SECRET, new SecureRandom()).decode(caseKey))
                .isEqualTo(descriptor);
    }

    @Test
    void rejectsTamperingAndKeysEncryptedWithAnotherSecret() {
        ContentStudioCaseKeyCodec codec = new ContentStudioCaseKeyCodec(SECRET, new SecureRandom());
        String caseKey = codec.encode(new ContentStudioCaseKeyCodec.CaseDescriptor(41L, 902L, 80, 40));
        char[] tampered = caseKey.toCharArray();
        tampered[tampered.length / 2] = tampered[tampered.length / 2] == 'A' ? 'B' : 'A';

        assertThatThrownBy(() -> codec.decode(new String(tampered)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> new ContentStudioCaseKeyCodec(
                "Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA=", new SecureRandom()).decode(caseKey))
                .isInstanceOf(CustomException.class);
    }
}
