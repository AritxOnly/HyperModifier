package miuix.mipalette;
import java.util.Map;
import java.util.HashMap;
/** Baseline API contract: named roles return transparent black until init populates the map. */
public final class MiPalette {
    static final Map<String, Integer> map = new HashMap<>();
    public static int initializations;
    public static void init() { initializations++; map.put("primary",0); map.put("tertiary",2); }
    public static int getPaletteColor(int color, String role, int tone) {
        return map.containsKey(role) ? color : 0;
    }
}
