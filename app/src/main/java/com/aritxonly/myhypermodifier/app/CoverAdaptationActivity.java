package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;

/** No-display shortcut target: does not replace the application being adapted. */
public final class CoverAdaptationActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CoverAdaptationContract.send(this, CoverAdaptationContract.TOGGLE);
        finish();
    }
}
