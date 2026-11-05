package com.aritxonly.myhypermodifier;

/** Internal, permission-protected handoff from system_server to SystemUI. */
public final class RecentsHandoffContract {
    public static final String ACTION_RECENTS_STARTED =
            BuildConfig.APPLICATION_ID + ".action.RECENTS_STARTED";
    public static final String ACTION_RECENTS_FINISHED =
            BuildConfig.APPLICATION_ID + ".action.RECENTS_FINISHED";
    public static final String PERMISSION_STATUS_BAR = "android.permission.STATUS_BAR";
    public static final String RECENTS_INPUT_CONSUMER = "recents_animation_input_consumer";

    private RecentsHandoffContract() {}
}
