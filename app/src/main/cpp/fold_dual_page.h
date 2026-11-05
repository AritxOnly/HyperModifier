#pragma once
#include "clock_glass.h"
void loadFoldDualPageConfig();
bool configureFoldDualPage(bool enabled);
void installFoldDualPage(ClockGlassImage dart, int (*hook)(void *, void *, void **),
        int (*unhook)(void *));
