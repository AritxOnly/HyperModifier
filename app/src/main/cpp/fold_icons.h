#pragma once
#include "clock_glass.h"
void loadFoldIconConfig();
bool configureFoldIconScale(unsigned percent);
void installFoldIcons(ClockGlassImage dart, int (*hook)(void *, void *, void **));
