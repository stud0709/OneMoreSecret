package com.onemoresecret.bt.layout;

public class KeyboardUsage {
    public static final int
    /**
     * No key pressed
     * */
            KBD_NONE = 0x00,
    /**
     * Keyboard Error Roll Over - used for all slots if too many keys are pressed ("Phantom key")
     */
    KBD_ERROR_ROLL_OVER = 0x01,
    /**
     * Keyboard POST Fail
     */
    KBD_POST_FAIL = 0x02,
    /**
     * Keyboard Error Undefined
     */
    KBD_ERROR_UNDEFINED = 0x03,
    /**
     * Keyboard a and A
     */
    KBD_A = 0x04,
    /**
     * Keyboard b and B
     */
    KBD_B = 0x05,
    /**
     * Keyboard c and C
     */
    KBD_C = 0x06,
    /**
     * Keyboard d and D
     */
    KBD_D = 0x07,
    /**
     * Keyboard e and E
     */
    KBD_E = 0x08,
    /**
     * Keyboard f and F
     */
    KBD_F = 0x09,
    /**
     * Keyboard g and G
     */
    KBD_G = 0x0a,
    /**
     * Keyboard h and H
     */
    KBD_H = 0x0b,
    /**
     * Keyboard i and I
     */
    KBD_I = 0x0c,
    /**
     * Keyboard j and J
     */
    KBD_J = 0x0d,
    /**
     * Keyboard k and K
     */
    KBD_K = 0x0e,
    /**
     * Keyboard l and L
     */
    KBD_L = 0x0f,
    /**
     * Keyboard m and M
     */
    KBD_M = 0x10,
    /**
     * Keyboard n and N
     */
    KBD_N = 0x11,
    /**
     * Keyboard o and O
     */
    KBD_O = 0x12,
    /**
     * Keyboard p and P
     */
    KBD_P = 0x13,
    /**
     * Keyboard q and Q
     */
    KBD_Q = 0x14,
    /**
     * Keyboard r and R
     */
    KBD_R = 0x15,
    /**
     * Keyboard s and S
     */
    KBD_S = 0x16,
    /**
     * Keyboard t and T
     */
    KBD_T = 0x17,
    /**
     * Keyboard u and U
     */
    KBD_U = 0x18,
    /**
     * Keyboard v and V
     */
    KBD_V = 0x19,
    /**
     * Keyboard w and W
     */
    KBD_W = 0x1a,
    /**
     * Keyboard x and X
     */
    KBD_X = 0x1b,
    /**
     * Keyboard y and Y
     */
    KBD_Y = 0x1c,
    /**
     * Keyboard z and Z
     */
    KBD_Z = 0x1d,
    /**
     * Keyboard 1 and !
     */
    KBD_1 = 0x1e,
    /**
     * Keyboard 2 and @
     */
    KBD_2 = 0x1f,
    /**
     * Keyboard 3 and #
     */
    KBD_3 = 0x20,
    /**
     * Keyboard 4 and $
     */
    KBD_4 = 0x21,
    /**
     * Keyboard 5 and %
     */
    KBD_5 = 0x22,
    /**
     * Keyboard 6 and ^
     */
    KBD_6 = 0x23,
    /**
     * Keyboard 7 and &
     */
    KBD_7 = 0x24,
    /**
     * Keyboard 8 and *
     */
    KBD_8 = 0x25,
    /**
     * Keyboard 9 and (
     */
    KBD_9 = 0x26,
    /**
     * Keyboard 0 and )
     */
    KBD_0 = 0x27,
    /**
     * Keyboard Return (ENTER)
     */
    KBD_ENTER = 0x28,
    /**
     * Keyboard ESCAPE
     */
    KBD_ESC = 0x29,
    /**
     * Keyboard DELETE (Backspace)
     */
    KBD_BACKSPACE = 0x2a,
    /**
     * Keyboard Tab
     */
    KBD_TAB = 0x2b,
    /**
     * Keyboard Spacebar
     */
    KBD_SPACE = 0x2c,
    /**
     * Keyboard - and _
     */
    KBD_MINUS = 0x2d,
    /**
     * Keyboard = and +
     */
    KBD_EQUAL = 0x2e,
    /**
     * Keyboard [ and {
     */
    KBD_LEFTBRACE = 0x2f,
    /**
     * Keyboard ] and }
     */
    KBD_RIGHTBRACE = 0x30,
    /**
     * Keyboard \ and |
     */
    KBD_BACKSLASH = 0x31,
    /**
     * Keyboard Non-US # and ~
     */
    KBD_HASHTILDE = 0x32,
    /**
     * Keyboard ; and :
     */
    KBD_SEMICOLON = 0x33,
    /**
     * Keyboard ' and "
     */
    KBD_APOSTROPHE = 0x34,
    /**
     * Keyboard ` and ~
     */
    KBD_GRAVE = 0x35,
    /**
     * Keyboard , and <
     */
    KBD_COMMA = 0x36,
    /**
     * Keyboard . and >
     */
    KBD_DOT = 0x37,
    /**
     * Keyboard / and ?
     */
    KBD_SLASH = 0x38,
    /**
     * Keyboard Caps Lock
     */
    KBD_CAPSLOCK = 0x39,
    /**
     * Keyboard F1
     */
    KBD_F1 = 0x3a,
    /**
     * Keyboard F2
     */
    KBD_F2 = 0x3b,
    /**
     * Keyboard F3
     */
    KBD_F3 = 0x3c,
    /**
     * Keyboard F4
     */
    KBD_F4 = 0x3d,
    /**
     * Keyboard F5
     */
    KBD_F5 = 0x3e,
    /**
     * Keyboard F6
     */
    KBD_F6 = 0x3f,
    /**
     * Keyboard F7
     */
    KBD_F7 = 0x40,
    /**
     * Keyboard F8
     */
    KBD_F8 = 0x41,
    /**
     * Keyboard F9
     */
    KBD_F9 = 0x42,
    /**
     * Keyboard F10
     */
    KBD_F10 = 0x43,
    /**
     * Keyboard F11
     */
    KBD_F11 = 0x44,
    /**
     * Keyboard F12
     */
    KBD_F12 = 0x45,
    /**
     * Keyboard Print Screen
     */
    KBD_SYSRQ = 0x46,
    /**
     * Keyboard Scroll Lock
     */
    KBD_SCROLLLOCK = 0x47,
    /**
     * Keyboard Pause
     */
    KBD_PAUSE = 0x48,
    /**
     * Keyboard Insert
     */
    KBD_INSERT = 0x49,
    /**
     * Keyboard Home
     */
    KBD_HOME = 0x4a,
    /**
     * Keyboard Page Up
     */
    KBD_PAGEUP = 0x4b,
    /**
     * Keyboard Delete Forward
     */
    KBD_DELETE = 0x4c,
    /**
     * Keyboard End
     */
    KBD_END = 0x4d,
    /**
     * Keyboard Page Down
     */
    KBD_PAGEDOWN = 0x4e,
    /**
     * Keyboard Right Arrow
     */
    KBD_RIGHT = 0x4f,
    /**
     * Keyboard Left Arrow
     */
    KBD_LEFT = 0x50,
    /**
     * Keyboard Down Arrow
     */
    KBD_DOWN = 0x51,
    /**
     * Keyboard Up Arrow
     */
    KBD_UP = 0x52,
    /**
     * Keypad Num Lock and Clear
     */
    KPAD_NUMLOCK = 0x53,
    /**
     * Keypad /
     */
    KPAD_SLASH = 0x54,
    /**
     * Keypad *
     */
    KPAD_ASTERISK = 0x55,
    /**
     * Keypad -
     */
    KPAD_MINUS = 0x56,
    /**
     * Keypad +
     */
    KPAD_PLUS = 0x57,
    /**
     * Keypad ENTER
     */
    KPAD_ENTER = 0x58,
    /**
     * Keypad 1 and End
     */
    KPAD_1 = 0x59,
    /**
     * Keypad 2 and Down Arrow
     */
    KPAD_2 = 0x5a,
    /**
     * Keypad 3 and PageDn
     */
    KPAD_3 = 0x5b,
    /**
     * Keypad 4 and Left Arrow
     */
    KPAD_4 = 0x5c,
    /**
     * Keypad 5
     */
    KPAD_5 = 0x5d,
    /**
     * Keypad 6 and Right Arrow
     */
    KPAD_6 = 0x5e,
    /**
     * Keypad 7 and Home
     */
    KPAD_7 = 0x5f,
    /**
     * Keypad 8 and Up Arrow
     */
    KPAD_8 = 0x60,
    /**
     * Keypad 9 and Page Up
     */
    KPAD_9 = 0x61,
    /**
     * Keypad 0 and Insert
     */
    KPAD_0 = 0x62,
    /**
     * Keypad . and Delete
     */
    KPAD_DOT = 0x63,
    /**
     * Keyboard Non-US \ and |
     */
    KBD_NON_US_BACKSLASH = 0x64,
    /**
     * Keyboard Application
     */
    KBD_APPLICATION = 0x65,
    /**
     * Keyboard Power
     */
    KBD_POWER = 0x66,
    /**
     * Keypad =
     */
    KPAD_EQUAL = 0x67,
    /**
     * Keyboard F13
     */
    KBD_F13 = 0x68,
    /**
     * Keyboard F14
     */
    KBD_F14 = 0x69,
    /**
     * Keyboard F15
     */
    KBD_F15 = 0x6a,
    /**
     * Keyboard F16
     */
    KBD_F16 = 0x6b,
    /**
     * Keyboard F17
     */
    KBD_F17 = 0x6c,
    /**
     * Keyboard F18
     */
    KBD_F18 = 0x6d,
    /**
     * Keyboard F19
     */
    KBD_F19 = 0x6e,
    /**
     * Keyboard F20
     */
    KBD_F20 = 0x6f,
    /**
     * Keyboard F21
     */
    KBD_F21 = 0x70,
    /**
     * Keyboard F22
     */
    KBD_F22 = 0x71,
    /**
     * Keyboard F23
     */
    KBD_F23 = 0x72,
    /**
     * Keyboard F24
     */
    KBD_F24 = 0x73,
    /**
     * Keyboard Execute
     */
    KBD_EXECUTE = 0x74,
    /**
     * Keyboard Help
     */
    KBD_HELP = 0x75,
    /**
     * Keyboard Menu
     */
    KBD_MENU = 0x76,
    /**
     * Keyboard Select
     */
    KBD_SELECT = 0x77,
    /**
     * Keyboard Stop
     */
    KBD_STOP = 0x78,
    /**
     * Keyboard Again
     */
    KBD_AGAIN = 0x79,
    /**
     * Keyboard Undo
     */
    KBD_UNDO = 0x7a,
    /**
     * Keyboard Cut
     */
    KBD_CUT = 0x7b,
    /**
     * Keyboard Copy
     */
    KBD_COPY = 0x7c,
    /**
     * Keyboard Paste
     */
    KBD_PASTE = 0x7d,
    /**
     * Keyboard Find
     */
    KBD_FIND = 0x7e,
    /**
     * Keyboard Mute
     */
    KBD_MUTE = 0x7f,
    /**
     * Keyboard Volume Up
     */
    KBD_VOLUME_UP = 0x80,
    /**
     * Keyboard Volume Down
     */
    KBD_VOLUME_DOWN = 0x81,
    /**
     * Keyboard Locking Caps Lock
     */
    KBD_LOCK_CAPSLOCK = 0x82,
    /**
     * Keyboard Locking Num Lock
     */
    KBD_LOCK_NUMLOCK = 0x83,
    /**
     * Keyboard Locking Scroll Lock
     */
    KBD_LOCK_SCROLLLOCK = 0x84,
    /**
     * Keypad Comma
     */
    KPAD_COMMA = 0x85,
    /**
     * Keypad Equal Sign
     */
    KPAD_EQUAL_SIGN = 0x86,
    /**
     * Keyboard International1
     */
    KBD_INTERNATIONAL1 = 0x87,
    /**
     * Keyboard International2
     */
    KBD_INTERNATIONAL2 = 0x88,
    /**
     * Keyboard International3
     */
    KBD_INTERNATIONAL3 = 0x89,
    /**
     * Keyboard International4
     */
    KBD_INTERNATIONAL4 = 0x8a,
    /**
     * Keyboard International5
     */
    KBD_INTERNATIONAL5 = 0x8b,
    /**
     * Keyboard International6
     */
    KBD_INTERNATIONAL6 = 0x8c,
    /**
     * Keyboard International7
     */
    KBD_INTERNATIONAL7 = 0x8d,
    /**
     * Keyboard International8
     */
    KBD_INTERNATIONAL8 = 0x8e,
    /**
     * Keyboard International9
     */
    KBD_INTERNATIONAL9 = 0x8f,
    /**
     * Keyboard LANG1
     */
    KBD_LANG1 = 0x90,
    /**
     * Keyboard LANG2
     */
    KBD_LANG2 = 0x91,
    /**
     * Keyboard LANG3
     */
    KBD_LANG3 = 0x92,
    /**
     * Keyboard LANG4
     */
    KBD_LANG4 = 0x93,
    /**
     * Keyboard LANG5
     */
    KBD_LANG5 = 0x94;
// 0x95  Keyboard LANG6
// 0x96  Keyboard LANG7
// 0x97  Keyboard LANG8
// 0x98  Keyboard LANG9
// 0x99  Keyboard Alternate Erase
// 0x9a  Keyboard SysReq/Attention
// 0x9b  Keyboard Cancel
// 0x9c  Keyboard Clear
// 0x9d  Keyboard Prior
// 0x9e  Keyboard Return
// 0x9f  Keyboard Separator
// 0xa0  Keyboard Out
// 0xa1  Keyboard Oper
// 0xa2  Keyboard Clear/Again
// 0xa3  Keyboard CrSel/Props
// 0xa4  Keyboard ExSel
// 0xb0  Keypad 00
// 0xb1  Keypad 000
// 0xb2  Thousands Separator
// 0xb3  Decimal Separator
// 0xb4  Currency Unit
// 0xb5  Currency Sub-unit
// 0xb6  Keypad (
// 0xb7  Keypad )
// 0xb8  Keypad {
// 0xb9  Keypad }
// 0xba  Keypad Tab
// 0xbb  Keypad Backspace
// 0xbc  Keypad A
// 0xbd  Keypad B
// 0xbe  Keypad C
// 0xbf  Keypad D
// 0xc0  Keypad E
// 0xc1  Keypad F
// 0xc2  Keypad XOR
// 0xc3  Keypad ^
// 0xc4  Keypad %
// 0xc5  Keypad <
// 0xc6  Keypad >
// 0xc7  Keypad &
// 0xc8  Keypad &&
// 0xc9  Keypad |
// 0xca  Keypad ||
// 0xcb  Keypad :
// 0xcc  Keypad #
// 0xcd  Keypad Space
// 0xce  Keypad @
// 0xcf  Keypad !
// 0xd0  Keypad Memory Store
// 0xd1  Keypad Memory Recall
// 0xd2  Keypad Memory Clear
// 0xd3  Keypad Memory Add
// 0xd4  Keypad Memory Subtract
// 0xd5  Keypad Memory Multiply
// 0xd6  Keypad Memory Divide
// 0xd7  Keypad +/-
// 0xd8  Keypad Clear
// 0xd9  Keypad Clear Entry
// 0xda  Keypad Binary
// 0xdb  Keypad Octal
// 0xdc  Keypad Decimal
// 0xdd  Keypad Hexadecimal

}
