package com.onemoresecret.bt;

public enum KeyModifier {
    NONE(0),
    LEFT_CONTROL(1),
    LEFT_SHIFT(1 << 1),
    LEFT_ALT(1 << 2),
    LEFT_GUI(1 << 3),
    RIGHT_CONTROL(1 << 4),
    RIGHT_SHIFT(1 << 5),
    RIGHT_ALT(1 << 6),
    RIGHT_GUI(1 << 7);

    public final byte value;

    KeyModifier(int value) {
        this.value = (byte) value;
    }
}