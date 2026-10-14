package com.aritxonly.myhypermodifier;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Filters the service's Widgets section to this user's Xiaomi launcher host. */
public final class DesktopWidgetInventoryParser {
    private static final Pattern ID = Pattern.compile("^\\s*\\[\\d+\\] id=(\\d+)\\s*$");
    private static final Pattern HOST = Pattern.compile("host=HostId\\{user:(\\d+),[^}]*hostId:1024, pkg:com\\.miui\\.home\\}");
    private static final Pattern PROVIDER = Pattern.compile("provider=.*cmp:ComponentInfo\\{([^}]+)\\}");
    private DesktopWidgetInventoryParser() {}
    public static ArrayList<String> parse(String dump, int user) {
        ArrayList<String> result = new ArrayList<>();
        boolean widgets = false, supported = false, home = false;
        int id = 0;
        for (String line : dump.split("\n")) {
            if (line.equals("Widgets:")) { widgets = supported = true; continue; }
            if (!widgets) continue;
            if (!line.isBlank() && !Character.isWhitespace(line.charAt(0))) break;
            Matcher entry = ID.matcher(line);
            if (entry.matches()) { id = Integer.parseInt(entry.group(1)); home = false; continue; }
            Matcher host = HOST.matcher(line);
            if (host.find()) { home = Integer.parseInt(host.group(1)) == user; continue; }
            Matcher provider = PROVIDER.matcher(line);
            if (id > 0 && home && provider.find()) {
                String component = provider.group(1);
                if (component.contains("/") && result.size() < 512) result.add(id + "\t" + component);
                home = false;
            }
        }
        if (!supported) throw new IllegalArgumentException("Unsupported widget inventory");
        return result;
    }
}
