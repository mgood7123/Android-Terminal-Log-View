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
package alpine.term.terminal_view;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import alpine.term.emulator.TerminalSession;

/**
 * Input and scale listener which may be set on a {@link TerminalView} through
 * {@link TerminalView#setOnKeyListener(TerminalViewClient)}.
 * <p/>
 */
public interface TerminalViewClient {
    /**
     * Callback function on scale events according to {@link ScaleGestureDetector#getScaleFactor()}.
     */
    float onScale(float scale);

    /**
     * On a single tap on the terminal if terminal mouse reporting not enabled.
     */
    void onSingleTapUp(MotionEvent e);

    void copyModeChanged(boolean copyMode);

    boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session);

    boolean onKeyUp(int keyCode, KeyEvent e);

    boolean readControlKey();

    boolean readAltKey();

    boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session);

    boolean onLongPress(MotionEvent event);
}
