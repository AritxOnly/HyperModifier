package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.Intent;

/** Explicit app and privileged SystemUI channels; never accepts arbitrary package launches. */
public final class CoverAdaptationContract {
    public static final String KEY = "cover_adaptation_enabled";
    public static final String PACKAGE = "com.aritxonly.myhypermodifier";
    public static final String PERMISSION = PACKAGE + ".permission.COVER_CONTROL";
    public static final String COMMAND = PACKAGE + ".COVER_COMMAND";
    public static final String SYSTEM_COMMAND = PACKAGE + ".COVER_SYSTEM_COMMAND";
    public static final String STATE = PACKAGE + ".COVER_STATE";
    public static final String TOGGLE = "toggle", EXIT = "exit", SWAP = "swap", QUERY = "query";
    public static final String ATTACH = "attach", SWITCH = "switch";

    public static void send(Context context, String command) {
        context.sendBroadcast(new Intent(COMMAND).setPackage("android").putExtra("command", command));
    }

    static void sendSystem(Context context, String command) {
        context.sendBroadcast(new Intent(SYSTEM_COMMAND).setPackage("android").putExtra("command", command));
    }

    private CoverAdaptationContract() {}
}
