package com.digitaleye.agent.monitor;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;

/**
 * Reads the title of the window currently in focus (the "foreground window").
 * This is the simplest, cheapest signal for "what application is the user using
 * right now" - jna-platform already wraps GetForegroundWindow / GetWindowText
 * for us, so no custom native binding is needed here.
 */
public class ActiveWindowDetector {

    private static final int MAX_TITLE_LENGTH = 1024;

    public String getActiveWindowTitle() {
        HWND foreground = User32.INSTANCE.GetForegroundWindow();
        if (foreground == null) {
            return "Unknown";
        }

        char[] buffer = new char[MAX_TITLE_LENGTH];
        User32.INSTANCE.GetWindowText(foreground, buffer, MAX_TITLE_LENGTH);
        String title = Native.toString(buffer);

        return title.isBlank() ? "Unknown" : title;
    }
}
