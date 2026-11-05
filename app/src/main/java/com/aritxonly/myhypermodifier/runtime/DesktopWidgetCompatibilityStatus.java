package com.aritxonly.myhypermodifier;

import java.util.HashMap;
import java.util.Map;

/** Wire parser kept Android-free for rejection and degradation regression tests. */
public final class DesktopWidgetCompatibilityStatus {
    public final int schema, picker, instances, bridge;
    public final boolean config;
    public final String reason;
    private final Map<String, String> values;
    private DesktopWidgetCompatibilityStatus(Map<String, String> values) {
        this.values = values;
        schema = stage(values.get("schema")); picker = stage(values.get("picker"));
        instances = stage(values.get("instances")); bridge = stage(values.get("bridge"));
        config = "1".equals(values.get("config")); reason = values.getOrDefault("reason", "unknown");
    }
    public static DesktopWidgetCompatibilityStatus parse(String text) {
        if (text == null || text.length() > 1024 || !text.startsWith("MHM_WIDGET_STATUS_V1\n"))
            throw new IllegalArgumentException("桌面兼容状态格式不匹配，请更新模块并重启桌面");
        Map<String, String> values = new HashMap<>();
        for (String line : text.split("\n")) {
            int index = line.indexOf('=');
            if (index > 0 && values.put(line.substring(0, index), line.substring(index + 1)) != null)
                throw new IllegalArgumentException("桌面兼容状态字段重复");
        }
        for (String key : new String[]{"schema", "picker", "instances", "bridge", "config", "reason"})
            if (!values.containsKey(key)) throw new IllegalArgumentException("桌面兼容状态不完整");
        if (!"0".equals(values.get("config")) && !"1".equals(values.get("config")))
            throw new IllegalArgumentException("桌面配置状态无效");
        if ("2".equals(values.get("fold_protocol"))) {
            stage(values.get("fold_stage"));
            for (String key : new String[]{"fold_received", "fold_requested", "fold_active", "fold_mode_seen", "fold_mode_applied", "fold_grid_seen", "fold_grid_applied"})
                if (!"0".equals(values.get(key)) && !"1".equals(values.get(key)))
                    throw new IllegalArgumentException("双页诊断状态不完整或无效");
        }
        return new DesktopWidgetCompatibilityStatus(values);
    }
    private static int stage(String value) {
        if (value == null || !value.matches("[0-4]")) throw new IllegalArgumentException("桌面兼容状态无效");
        return value.charAt(0) - '0';
    }
    public String foldSummary(boolean wanted) {
        if (!"2".equals(values.get("fold_protocol")))
            return "桌面仍在运行旧版模块，请确认已覆盖安装本版并重启手机。";
        String prefix = "设置桥 " + label(bridge) + "\n";
        if (!"1".equals(values.get("fold_received")))
            return prefix + "桌面尚未收到新版双页设置。请确认系统界面作用域，并重启系统界面。";
        boolean requested = "1".equals(values.get("fold_requested"));
        if (wanted != requested)
            return prefix + "界面开关与桌面收到的设置不一致，请检查模块总开关、兼容模式和设置同步。";
        if (!wanted) return prefix + "桌面已收到关闭设置。重启桌面可重建原布局。";
        String stage = values.get("fold_stage");
        if ("3".equals(stage)) return prefix + "桌面版本或代码指纹不匹配，双页 Hook 未安装。";
        if ("4".equals(stage)) return prefix + "双页 Hook 安装失败，已停用此次修改。";
        if (!"2".equals(stage)) return prefix + "双页设置已送达，仍在等待桌面代码加载。";
        if (!"1".equals(values.get("fold_active"))) return prefix + "Hook 已就绪，但运行开关未激活。";
        return prefix + "双页 Hook 已安装并启用\n"
                + "双页判断 " + hit("mode") + "\n网格计算 " + hit("grid")
                + "\n以上命中记录从本次桌面进程启动累计。请展开竖屏、回桌面后再点刷新。";
    }
    private String hit(String name) {
        if ("1".equals(values.get("fold_" + name + "_applied"))) return "已执行竖屏修改";
        if ("1".equals(values.get("fold_" + name + "_seen"))) return "入口已调用，竖屏条件未通过";
        return "尚未调用";
    }
    public boolean needsSchemaLibrary() { return schema == 2; }
    private static String label(int stage) {
        switch (stage) {
            case 1: return "就绪";
            case 2: return "不兼容";
            case 3: return "安装失败";
            case 4: return "等待超时";
            default: return "等待初始化";
        }
    }
    public String summary() {
        return "基础能力：" + label(schema) + "\n添加列表：" + label(picker)
                + " · 实例规则：" + label(instances) + "\n设置桥：" + label(bridge)
                + " · " + (config ? "已收到设置" : "尚未收到设置")
                + (picker == 2 || picker == 3 ? "\n添加列表适配已停用，基础能力状态独立显示。" : "")
                + (schema == 3 ? "\nHook 安装失败，请提供启动日志。" : "");
    }
}
