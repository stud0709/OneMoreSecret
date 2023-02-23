package com.onemoresecret;

public final class Util {
    private Util() {
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < a.length; i++) {
            sb.append(String.format("%02x", a[i]));

            if (i++ % 2 == 1 && i < a.length - 1)
                sb.append(" ");
        }
        return sb.toString();
    }
}
