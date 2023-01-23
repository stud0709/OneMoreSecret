package com.onemoresecret.bt;

public class KeyboardReport {
    public final byte[] report;

    public static final int
            LEFT_CONTROL = 1,
            LEFT_SHIFT = 1 << 1,
            LEFT_ALT = 1 << 2,
            LEFT_GUI = 1 << 3,
            RIGHT_CONTROL = 1 << 4,
            RIGHT_SHIFT = 1 << 5,
            RIGHT_ALT = 1 << 6,
            RIGHT_GUI = 1 << 7;

    public static final int
            NUM_LOCK = 1,
            CAPS_LOCK = 1 << 1,
            SCROLL_LOCK = 1 << 2,
            COMPOSE = 1 << 3,
            KANA = 1 << 4;


    /**
     * Report data.
     *
     * @param modifiers Use {@link KeyboardReport#LEFT_CONTROL} and other constants combined with boolean AND, e.g. {@code LEFT_CONTROL & LEFT_SHIFT }
     * @param key       Physical key according to USB HID Usage Tables.
     */
    public KeyboardReport(int modifiers, int key) {
        report = new byte[]{(byte) modifiers, (byte) key};
    }

    public KeyboardReport addModifier(int modifier) {
        return new KeyboardReport(report[0] | modifier, report[1]);
    }

    public KeyboardReport removeModifier(int modifier) {
        return new KeyboardReport(report[0] & ~modifier, report[1]);
    }
}
