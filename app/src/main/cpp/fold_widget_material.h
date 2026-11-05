#pragma once
#include "clock_glass.h"
void installFoldWidgetMaterial(ClockGlassImage dart, int (*hook)(void *, void *, void **),
        int (*unhook)(void *), void (*prepareRules)());
