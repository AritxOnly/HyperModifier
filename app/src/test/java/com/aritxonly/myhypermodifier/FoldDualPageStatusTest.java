package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class FoldDualPageStatusTest {
    private static final String BASE = "MHM_WIDGET_STATUS_V1\nschema=1\npicker=1\ninstances=1\nbridge=1\nconfig=1\nreason=test\n";
    private static String report(String extra) {
        return BASE + "fold_protocol=2\nfold_stage=2\nfold_received=1\nfold_requested=1\nfold_active=1\n"
            + "fold_mode_seen=0\nfold_mode_applied=0\nfold_grid_seen=0\nfold_grid_applied=0\n" + extra;
    }
    private static String summary(String text) { return DesktopWidgetCompatibilityStatus.parse(text).foldSummary(true); }
    @Test public void readinessDoesNotClaimHooksWereExecuted() {
        String report = report("");
        assertTrue(summary(report).contains("尚未调用"));
        assertFalse(summary(report).contains("已执行竖屏修改"));
        assertTrue(summary(report.replace("fold_grid_seen=0", "fold_grid_seen=1")).contains("竖屏条件未通过"));
        assertTrue(summary(report.replace("fold_grid_applied=0", "fold_grid_applied=1")).contains("网格计算 已执行竖屏修改"));
    }
    @Test public void unavailableOldAndUnsyncedStatesAreDistinct() {
        assertTrue(summary(BASE).contains("旧版模块"));
        assertTrue(summary(report("").replace("fold_received=1", "fold_received=0")).contains("尚未收到"));
        assertTrue(summary(report("").replace("fold_requested=1", "fold_requested=0")).contains("不一致"));
        assertTrue(summary(report("").replace("fold_stage=2", "fold_stage=3")).contains("指纹不匹配"));
        assertTrue(summary(report("").replace("fold_stage=2", "fold_stage=4")).contains("安装失败"));
        assertTrue(DesktopWidgetCompatibilityStatus.parse(report("").replace("fold_requested=1", "fold_requested=0")).foldSummary(false).contains("收到关闭"));
    }
    @Test public void invalidOrMissingDiagnosticsAreRejected() {
        for (String text : new String[]{report("").replace("fold_grid_seen=0\n", ""), report("").replace("fold_active=1", "fold_active=yes"), report("fold_stage=2\n")}) {
            try { summary(text); fail(); } catch (IllegalArgumentException expected) { }
        }
    }
}
