package com.github.eka2l1.phone;

import com.github.eka2l1.emu.Keycode;
import com.github.eka2l1.settings.AppDataStore;

import java.util.HashMap;
import java.util.Map;

/** Persistent per-handset mappings for every physical and navigation key. */
public final class PhoneKeyMappings {
    private static final String STORE_KEY = "phone-key-mappings";
    private static Map<String, String> cachedMappings;

    private PhoneKeyMappings() {}

    public static int get(String asset, String control, int fallback, boolean ngageGame) {
        String key = asset + ":" + control;
        Map<String, String> mappings = mappings();
        String saved = mappings.get(key);
        if (saved != null) {
            try {
                return Integer.decode(saved);
            } catch (NumberFormatException ignored) {
                // An invalid stored entry falls back to the handset default.
            }
        }
        if ("n86".equals(asset) && ngageGame) {
            if ("key:7".equals(control)) return Keycode.KEY_A;
            if ("key:8".equals(control)) return Keycode.KEY_B;
        }
        if ("n86".equals(asset)) {
            switch (control) {
                case "key:6": return Keycode.KEY_STAR;
                case "key:7": return Keycode.KEY_NUM5;
                case "key:8": return Keycode.KEY_NUM8;
                case "key:9": return Keycode.KEY_POUND;
            }
        }
        return fallback;
    }

    public static void put(String asset, String control, int value) {
        AppDataStore store = AppDataStore.getAndroidStore();
        Map<String, String> mappings = mappings();
        mappings.put(asset + ":" + control, Integer.toString(value));
        cachedMappings = mappings;
        store.putStringMap(STORE_KEY, mappings);
        store.save();
    }

    public static void reset(String asset, String control) {
        AppDataStore store = AppDataStore.getAndroidStore();
        Map<String, String> mappings = mappings();
        mappings.remove(asset + ":" + control);
        cachedMappings = mappings;
        store.putStringMap(STORE_KEY, mappings);
        store.save();
    }

    private static synchronized Map<String, String> mappings() {
        if (cachedMappings == null) {
            cachedMappings = AppDataStore.getAndroidStore().getStringMap(STORE_KEY);
        }
        return new HashMap<>(cachedMappings);
    }

    public static int defaultValue(String asset, String control) {
        if ("n86".equals(asset)) {
            switch (control) {
                case "key:6": return Keycode.KEY_STAR;
                case "key:7": return Keycode.KEY_NUM5;
                case "key:8": return Keycode.KEY_NUM8;
                case "key:9": return Keycode.KEY_POUND;
                case "nav:up": return Keycode.KEY_UP;
                case "nav:down": return Keycode.KEY_DOWN;
                case "nav:left": return Keycode.KEY_LEFT;
                case "nav:right": return Keycode.KEY_RIGHT;
            }
        }
        switch (control) {
            case "nav:up": return Keycode.KEY_UP;
            case "nav:down": return Keycode.KEY_DOWN;
            case "nav:left": return Keycode.KEY_LEFT;
            case "nav:right": return Keycode.KEY_RIGHT;
            case "nav:center": return Keycode.KEY_FIRE;
            default: return 0;
        }
    }

    public static String label(int code) {
        switch (code) {
            case Keycode.KEY_UP: return "上";
            case Keycode.KEY_DOWN: return "下";
            case Keycode.KEY_LEFT: return "左";
            case Keycode.KEY_RIGHT: return "右";
            case Keycode.KEY_FIRE: return "中键";
            case Keycode.KEY_SOFT_LEFT: return "左软键";
            case Keycode.KEY_SOFT_RIGHT: return "右软键";
            case Keycode.KEY_CALL: return "拨号";
            case Keycode.KEY_END: return "挂断";
            case Keycode.KEY_MENU: return "菜单";
            case Keycode.KEY_CLEAR: return "清除";
            case Keycode.KEY_SEND: return "发送";
            case Keycode.KEY_A: return "A";
            case Keycode.KEY_B: return "B";
            case Keycode.KEY_POUND: return "#";
            case Keycode.KEY_STAR: return "*";
            default:
                if (code >= '0' && code <= '9') return Character.toString((char) code);
                return String.format("0x%02X", code);
        }
    }

    public static int parse(String value) {
        String text = value.trim();
        switch (text.toUpperCase(java.util.Locale.ROOT)) {
            case "UP": case "上": return Keycode.KEY_UP;
            case "DOWN": case "下": return Keycode.KEY_DOWN;
            case "LEFT": case "左": return Keycode.KEY_LEFT;
            case "RIGHT": case "右": return Keycode.KEY_RIGHT;
            case "FIRE": case "中键": return Keycode.KEY_FIRE;
            case "SOFT_LEFT": case "左软键": return Keycode.KEY_SOFT_LEFT;
            case "SOFT_RIGHT": case "右软键": return Keycode.KEY_SOFT_RIGHT;
            case "CALL": case "拨号": return Keycode.KEY_CALL;
            case "END": case "挂断": return Keycode.KEY_END;
            case "MENU": case "菜单": return Keycode.KEY_MENU;
            case "CLEAR": case "清除": return Keycode.KEY_CLEAR;
            case "A": return Keycode.KEY_A;
            case "B": return Keycode.KEY_B;
            case "*": return Keycode.KEY_STAR;
            case "#": return Keycode.KEY_POUND;
            default:
                if (text.length() == 1) return text.charAt(0);
                return Integer.decode(text);
        }
    }
}
