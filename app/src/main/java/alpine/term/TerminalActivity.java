/*
*************************************************************************
Alpine Term - a VM-based terminal emulator.
Copyright (C) 2019-2020  Leonid Plyushch <leonid.plyushch@gmail.com>

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
package alpine.term;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import alpine.term.emulator.JNI;
import alpine.term.emulator.TerminalColors;
import alpine.term.emulator.TerminalSession;
import alpine.term.emulator.TerminalSession.SessionChangedCallback;
import alpine.term.emulator.TextStyle;
import alpine.term.terminal_view.TerminalView;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TerminalActivity extends Activity {

    TerminalController terminalController;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // set log view
        setContentView(R.layout.slide_out_terminal);

        // obtain log view instance
        terminalController = new TerminalController();
        terminalController.onCreate(this, findViewById(R.id.terminal_view));


        Button toggleTerminal = findViewById(R.id.toggle_terminal);
        int visibility = terminalController.terminalContainer.getVisibility();
        if (visibility == View.INVISIBLE) {
            toggleTerminal.setText(R.string.Show_LogTerminal);
        } else {
            toggleTerminal.setText(R.string.Hide_LogTerminal);
        }
        toggleTerminal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int visibility = terminalController.terminalContainer.getVisibility();
                if (visibility == View.INVISIBLE) {
                    // if terminal is not shown, and this button is clicked, then show the terminal
                    terminalController.mainView.setVisibility(View.INVISIBLE);
                    terminalController.terminalContainer.setVisibility(View.VISIBLE);
                    toggleTerminal.setText(R.string.Hide_LogTerminal);
                } else {
                    // if terminal is shown, and this button is clicked, then hide the terminal
                    terminalController.terminalContainer.setVisibility(View.INVISIBLE);
                    terminalController.mainView.setVisibility(View.VISIBLE);
                    toggleTerminal.setText(R.string.Show_LogTerminal);
                }
            }
        });

        RelativeLayout rl = findViewById(R.id.mainView);

        TextView tv = new TextView(this);

        tv.setTypeface(Typeface.MONOSPACE);
        tv.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        tv.setPaintFlags(tv.getPaintFlags() | Paint.ANTI_ALIAS_FLAG);
        tv.setTextSize(24);

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("1. Tap on SHOW LOG/TERMINAL\n");
        stringBuilder.append("2. Slide -> from the left of the screen to open\n");
        stringBuilder.append("   the terminal list\n");
        stringBuilder.append("\nThe default terminal is the log terminal\n");
        stringBuilder.append("    This terminal prints all stdout and stderr\n");
        stringBuilder.append("    output from the current Application\n");
        stringBuilder.append("    (this application), and it cannot be removed\n");
        stringBuilder.append("\nTap on the \"Add Shell\" button to create a new\n");
        stringBuilder.append("shell\n");
        tv.setText(stringBuilder.toString());

        rl.addView(tv);
    }

    @Override
    protected void onStart() {
        super.onStart();
        terminalController.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        terminalController.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        terminalController.onDestroy();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        terminalController.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return terminalController.onContextItemSelected(item) || super.onContextItemSelected(item);
    }

    /**
     * Hook system menu to show context menu instead.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        terminalController.onCreateOptionsMenu(menu);
        return false;
    }

    @Override
    public void onBackPressed() {
        if (!terminalController.onBackPressed()) super.onBackPressed();
    }
}
