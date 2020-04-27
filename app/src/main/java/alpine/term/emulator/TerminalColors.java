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

/** Current terminal colors (if different from default). */
public final class TerminalColors {

    /** Static data - a bit ugly but ok for now. */
    public static final TerminalColorScheme COLOR_SCHEME = new TerminalColorScheme();

    /**
     * The current terminal colors, which are normally set from the color theme, but may be set dynamically with the OSC
     * 4 control sequence.
     */
    public final int[] mCurrentColors = new int[TextStyle.NUM_INDEXED_COLORS];

    /** Create a new instance with default colors from the theme. */
    public TerminalColors() {
        reset();
    }

    /** Reset a particular indexed color with the default color from the color theme. */
    public void reset(int index) {
        mCurrentColors[index] = COLOR_SCHEME.mDefaultColors[index];
    }

    /** Reset all indexed colors with the default color from the color theme. */
    public void reset() {
        System.arraycopy(COLOR_SCHEME.mDefaultColors, 0, mCurrentColors, 0, TextStyle.NUM_INDEXED_COLORS);
    }

    /**
     * Parse color according to http://manpages.ubuntu.com/manpages/intrepid/man3/XQueryColor.3.html
     * <p/>
     * Highest bit is set if successful, so return value is 0xFF${R}${G}${B}. Return 0 if failed.
     */
    static int parse(String c) {
        try {
            int skipInitial, skipBetween;
            if (c.charAt(0) == '#') {
                // #RGB, #RRGGBB, #RRRGGGBBB or #RRRRGGGGBBBB. Most significant bits.
                skipInitial = 1;
                skipBetween = 0;
            } else if (c.startsWith("rgb:")) {
                // rgb:<red>/<green>/<blue> where <red>, <green>, <blue> := h | hh | hhh | hhhh. Scaled.
                skipInitial = 4;
                skipBetween = 1;
            } else {
                return 0;
            }
            int charsForColors = c.length() - skipInitial - 2 * skipBetween;
            if (charsForColors % 3 != 0) return 0; // Unequal lengths.
            int componentLength = charsForColors / 3;
            double mult = 255 / (Math.pow(2, componentLength * 4) - 1);

            int currentPosition = skipInitial;
            String rString = c.substring(currentPosition, currentPosition + componentLength);
            currentPosition += componentLength + skipBetween;
            String gString = c.substring(currentPosition, currentPosition + componentLength);
            currentPosition += componentLength + skipBetween;
            String bString = c.substring(currentPosition, currentPosition + componentLength);

            int r = (int) (Integer.parseInt(rString, 16) * mult);
            int g = (int) (Integer.parseInt(gString, 16) * mult);
            int b = (int) (Integer.parseInt(bString, 16) * mult);
            return 0xFF << 24 | r << 16 | g << 8 | b;
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return 0;
        }
    }

    /** Try parse a color from a text parameter and into a specified index. */
    public void tryParseColor(int intoIndex, String textParameter) {
        int c = parse(textParameter);
        if (c != 0) mCurrentColors[intoIndex] = c;
    }

}
