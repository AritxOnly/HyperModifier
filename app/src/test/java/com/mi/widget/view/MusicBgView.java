package com.mi.widget.view;
import android.content.Context;
import android.widget.FrameLayout;
/** Contract fixture only; the production renderer is supplied by the ROM. */
public class MusicBgView extends FrameLayout {
    public int starts, resumes, stops, colorUpdates, base;
    public int[] colors;
    public MusicBgView(Context context) { super(context); }
    public void start() { starts++; }
    public void resume() { resumes++; }
    public void stop() { stops++; }
    public void setGradientColor(int base, int[] colors) { this.base = base; this.colors = colors; colorUpdates++; }
}
