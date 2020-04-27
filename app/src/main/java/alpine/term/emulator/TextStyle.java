/*
*************************************************************************
Alpine Term - a VM-based terminal emulator.
Copyright (C) 2019-2020  Leonid Plyushch <leonid.plyushch@gmail.com>

Originally was part of Termux.
Copyright (C) 2019  Fredrik Fornwall <fredrik@fornwall.net>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*************************************************************************
*/
package alpine.term.emulator;

/**
 * <p>
 * Encodes effects, foreground and background colors into a 64 bit long, which are stored for each cell in a terminal
 * row in {@link TerminalRow#mStyle}.
 * </p>
 * <p>
 * The bit layout is:
 * </p>
 * - 16 flags (11 currently used).
 * - 24 for foreground color (only 9 first bits if a color index).
 * - 24 for background color (only 9 first bits if a color index).
 */
public final class TextStyle {

    public final static int CHARACTER_ATTRIBUTE_BOLD = 1;
    public final static int CHARACTER_ATTRIBUTE_ITALIC = 1 << 1;
    public final static int CHARACTER_ATTRIBUTE_UNDERLINE = 1 << 2;
    public final static int CHARACTER_ATTRIBUTE_BLINK = 1 << 3;
    public final static int CHARACTER_ATTRIBUTE_INVERSE = 1 << 4;
    public final static int CHARACTER_ATTRIBUTE_INVISIBLE = 1 << 5;
    public final static int CHARACTER_ATTRIBUTE_STRIKETHROUGH = 1 << 6;
    /**
     * The selective erase control functions (DECSED and DECSEL) can only erase characters defined as erasable.
     * <p>
     * This bit is set if DECSCA (Select Character Protection Attribute) has been used to define the characters that
     * come after it as erasable from the screen.
     * </p>
     */
    public final static int CHARACTER_ATTRIBUTE_PROTECTED = 1 << 7;
    /** Dim colors. Also known as faint or half intensity. */
    public final static int CHARACTER_ATTRIBUTE_DIM = 1 << 8;
    /** If true (24-bit) color is used for the cell for foreground. */
    private final static int CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND = 1 << 9;
    /** If true (24-bit) color is used for the cell for foreground. */
    private final static int CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND= 1 << 10;

    public final static int COLOR_INDEX_FOREGROUND = 256;
    public final static int COLOR_INDEX_BACKGROUND = 257;
    public final static int COLOR_INDEX_CURSOR = 258;

    /** The 256 standard color entries and the three special (foreground, background and cursor) ones. */
    public final static int NUM_INDEXED_COLORS = 259;

    /** Normal foreground and background colors and no effects. */
    final static long NORMAL = encode(COLOR_INDEX_FOREGROUND, COLOR_INDEX_BACKGROUND, 0);

    static long encode(int foreColor, int backColor, int effect) {
        long result = effect & 0b111111111;
        if ((0xff000000 & foreColor) == 0xff000000) {
            // 24-bit color.
            result |= CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND | ((foreColor & 0x00ffffffL) << 40L);
        } else {
            // Indexed color.
            result |= (foreColor & 0b111111111L) << 40;
        }
        if ((0xff000000 & backColor) == 0xff000000) {
            // 24-bit color.
            result |= CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND | ((backColor & 0x00ffffffL) << 16L);
        } else {
            // Indexed color.
            result |= (backColor & 0b111111111L) << 16L;
        }

        return result;
    }

    public static int decodeForeColor(long style) {
        if ((style & CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND) == 0) {
            return (int) ((style >>> 40) & 0b111111111L);
        } else {
            return 0xff000000 | (int) ((style >>> 40) & 0x00ffffffL);
        }

    }

    public static int decodeBackColor(long style) {
        if ((style & CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND) == 0) {
            return (int) ((style >>> 16) & 0b111111111L);
        } else {
            return 0xff000000 | (int) ((style >>> 16) & 0x00ffffffL);
        }
    }

    public static int decodeEffect(long style) {
        return (int) (style & 0b11111111111);
    }

}
