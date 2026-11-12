package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.widget.ImageView;

interface ShortcutMaterial {
    int generation(Context context);
    int currentGeneration();
    void material(ImageView view) throws ReflectiveOperationException;
}
