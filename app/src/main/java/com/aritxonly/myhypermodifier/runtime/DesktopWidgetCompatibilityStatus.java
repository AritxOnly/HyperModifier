package com.aritxonly.myhypermodifier;

import java.util.HashMap;
import java.util.Map;

/** Wire parser kept Android-free for rejection and degradation regression tests. */
public final class DesktopWidgetCompatibilityStatus {
    public final int schema, picker, instances, bridge;
    public final boolean config;
    public final String reason;
    private DesktopWidgetCompatibilityStatus(Map<String, String> values) {
        schema = stage(values.get("schema")); picker = stage(values.get("picker"));
        instances = stage(values.get("instances")); bridge = stage(values.get("bridge"));
        config = "1".equals(values.get("config")); reason = values.getOrDefault("reason", "unknown");
    }
    public static DesktopWidgetCompatibilityStatus parse(String text) {
        if (text == null || text.length() > 768 || !text.startsWith("MHM_WIDGET_STATUS_V1\n"))
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
        return new DesktopWidgetCompatibilityStatus(values);
    }
    private static int stage(String value) {
        if (value == null || !value.matches("[0-4]")) throw new IllegalArgumentException("桌面兼容状态无效");
        return value.charAt(0) - '0';
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
