#!/usr/bin/env python3
"""Check actual local SystemUI smali contracts used by shortcut and bottom hooks."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
CONTRACTS = {
    'miui/stub/MiuiStub': ['INSTANCE:Lmiui/stub/MiuiStub;', 'mSysUIProvider:Lmiui/stub/MiuiStub$SysUIProvider;'],
    'miui/stub/MiuiStub$SysUIProvider': ['mCentralSurfaces:Ldagger/Lazy;'],
    'com/android/systemui/statusbar/phone/CentralSurfacesImpl': ['mStackScrollerController:Lcom/android/systemui/statusbar/notification/stack/NotificationStackScrollLayoutController;'],
    'com/android/systemui/statusbar/notification/stack/NotificationStackScrollLayoutController': ['mNsslControllerInjector:Lcom/android/systemui/statusbar/notification/stack/NotificationStackScrollLayoutControllerInjectorImpl;'],
    'com/android/keyguard/panel/KeyguardPanelViewController': ['miuiShortcutControllerLazy:Ldagger/Lazy;'],
    'com/android/keyguard/shortcut/MiuiShortcutController': [
        'shortcutContainerView:Landroid/widget/FrameLayout;',
        'addShortcutViews(Landroid/widget/FrameLayout;)V',
        'onSystemUIAction$1(Landroid/os/Bundle;Ljava/lang/String;)Ljava/lang/Object;',
        'statusBarStateController$delegate:Lkotlin/Lazy;',
    ],
    'com/android/systemui/statusbar/StatusBarStateControllerImpl': ['mState:I', 'mIsDozing:Z'],
    'com/android/systemui/statusbar/notification/stack/NotificationStackScrollLayoutControllerInjectorImpl': [
        'updateStackingBottomForMiui(F)V', 'stackingBottom:F',
        'layout:Lcom/android/systemui/statusbar/notification/stack/NotificationStackScrollLayout;',
        'notifContainerViewModel:Ldagger/Lazy;',
        'keyguardPanelViewController:Ldagger/Lazy;',
    ],
    'com/android/systemui/statusbar/notification/stack/NotificationStackScrollLayout': [
        'requestChildrenUpdate()V', 'mAnimationsEnabled:Z', 'mIsExpanded:Z',
        'mTopPaddingNeedsAnimation:Z', 'mNeedsAnimation:Z',
    ],
    'com/miui/systemui/notification/view/viewmodel/NotificationContainerViewModel': [
        'isShowingKeyguardNotification()Lcom/miui/coroutines/syncflow/SynchronousStateFlow;',
    ],
    'com/android/systemui/statusbar/notification/utils/NotificationUtil': [
        'applyContainerViewBlur(Landroid/content/Context;Landroid/view/View;IIZ)V',
        'applyElementViewBlend(Landroid/content/Context;Landroid/view/View;Z[IZ)V',
    ],
    'com/miui/systemui/util/MiGlassCompat': [
        'setMiGlassCompat(Landroid/view/View;[F)V',
        'setMiViewMaterialTypeCompat(ILandroid/view/View;)V',
    ],
    'com/android/systemui/statusbar/notification/row/ExpandableNotificationRow': [
        'getInjector()Lcom/android/systemui/statusbar/notification/row/ExpandableNotificationRowInjector;',
    ],
    'com/android/systemui/statusbar/notification/row/ExpandableNotificationRowInjector': [
        'getBackgroundNormal()Lcom/android/systemui/statusbar/notification/row/NotificationBackgroundView;',
    ],
}
for effect in ['NotificationRowGlassOnKeyguardEffect', 'NotificationRowGlassOnKeyguardLightWallPaperEffect']:
    CONTRACTS['com/android/systemui/statusbar/notification/style/vieweffect/' + effect] = [
        'apply(Ljava/lang/Object;Landroid/content/Context;)V']

def verify(directory):
    count = 0
    for name, members in CONTRACTS.items():
        found = list(directory.glob('smali*/' + name + '.smali'))
        assert len(found) == 1, f'{directory.name}: missing/ambiguous class {name}'
        data = found[0].read_text()
        for member in members:
            assert re.search(r'^\.(?:method|field) [^\n]*' + re.escape(member) + r'\s*$', data, re.M), f'{directory.name}: {name}.{member}'
            count += 1
    print(f'{directory.name}: PASS {count} native member contracts (static smali check)')

for path in sys.argv[1:] or ['reference/systemui-4.0.2.0', 'reference/systemui-fold-4.0.26.0']:
    verify(ROOT / path)
