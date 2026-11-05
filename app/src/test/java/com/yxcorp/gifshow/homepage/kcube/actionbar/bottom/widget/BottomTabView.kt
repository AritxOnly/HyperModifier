package com.yxcorp.gifshow.homepage.kcube.actionbar.bottom.widget

import android.content.Context
import android.widget.FrameLayout

class BottomTabView(context: Context, private val label: String) : FrameLayout(context) {
    fun getText(): CharSequence = label
}
