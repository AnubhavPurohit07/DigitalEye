package com.digitaleye.agent.monitor;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.Arrays;
import java.util.List;

/**
 * Reads how long the system has had no keyboard/mouse input, using the Win32
 * GetLastInputInfo API. jna-platform doesn't wrap this call out of the box,
 * so we declare the tiny native interface ourselves - this is the standard
 * pattern for calling a Win32 function JNA hasn't already mapped for you.
 */
public class IdleTimeDetector {

    private interface User32Idle extends StdCallLibrary {
        User32Idle INSTANCE = Native.load("user32", User32Idle.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean GetLastInputInfo(LastInputInfo result);
    }

    private interface Kernel32Idle extends StdCallLibrary {
        Kernel32Idle INSTANCE = Native.load("kernel32", Kernel32Idle.class, W32APIOptions.DEFAULT_OPTIONS);

        int GetTickCount();
    }

    /** Mirrors the native LASTINPUTINFO struct: { UINT cbSize; DWORD dwTime; } */
    public static class LastInputInfo extends Structure {
        public int cbSize = 8; // size of this struct in bytes (two 4-byte fields)
        public int dwTime;     // tick count at the last input event

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("cbSize", "dwTime");
        }
    }

    /**
     * @return milliseconds since the last keyboard or mouse input was received
     */
    public long getIdleTimeMillis() {
        LastInputInfo info = new LastInputInfo();
        User32Idle.INSTANCE.GetLastInputInfo(info);
        int currentTicks = Kernel32Idle.INSTANCE.GetTickCount();
        return currentTicks - info.dwTime;
    }
}
