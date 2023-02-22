package com.onemoresecret.crypto;

import android.content.SharedPreferences;

public final class RSAUtils {
    public static final String PROP_RSA_TRANSFORMATION_IDX = "rsa_transformation_idx", PROP_RSA_KEY_LENGTH = "rsa_key_length";

    private RSAUtils() {

    }

    public static int getRsaTransformationIdx(SharedPreferences preferences) {
        return preferences.getInt(PROP_RSA_TRANSFORMATION_IDX, 0);
    }

    public static RsaTransformation getRsaTransformation(SharedPreferences preferences) {
        return RsaTransformation.values()[getRsaTransformationIdx(preferences)];
    }

    public static int getKeyLength(SharedPreferences preferences) {
        return preferences.getInt(PROP_RSA_KEY_LENGTH, 2048);
    }

}
