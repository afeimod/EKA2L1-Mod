package com.github.eka2l1.phone;

import java.util.Locale;

/** Shells are independent of ROM availability; an absent ROM never borrows another device's apps. */
public enum PhoneModel {
    N73("Nokia N73", "S60V3", "n73", 430, 932, 47, 85, 332, 436),
    QD("N-Gage QD", "S60V1 / V2", "qd", 1000, 579, 354, 96, 312, 369),
    N86("Nokia N86", "S60V3 FP2", "n86", 430, 990, 48, 118, 334, 445),
    N86_MEDIA("N86 横屏", "S60V3", "n86", 1060, 490, 315, 49, 506, 381),
    N8("Nokia N8", "S60V5 / Symbian^3", "n8", 430, 850, 53, 132, 324, 586),
    N8_LANDSCAPE("Nokia N8 横屏", "S60V5 / Symbian^3", "n8", 850, 430, 132, 53, 586, 324);

    public static final String PREF = "phone-shell";
    public final String title, platform;
    public final float width, height;
    public final android.graphics.RectF screen;
    public final String asset;

    PhoneModel(String title, String platform, String asset, float width, float height,
               float x, float y, float w, float h) {
        this.title = title;
        this.platform = platform;
        this.width = width;
        this.height = height;
        this.asset = asset;
        screen = new android.graphics.RectF(x, y, x + w, y + h);
    }

    public static PhoneModel parse(String name) {
        try { return valueOf(name); } catch (Exception ignored) { return N73; }
    }

    public static PhoneModel forDevice(String name, String code, boolean touch) {
        String value = (name + " " + code).toLowerCase(Locale.ROOT);
        if (value.contains("gage") || value.contains("rh-29")) return QD;
        if (touch) return N8;
        try {
            java.io.File file = new java.io.File(com.github.eka2l1.emu.Emulator.getEmulatorDir(), "data/devices.yml");
            try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
                java.util.Map<?, ?> devices = new org.yaml.snakeyaml.Yaml().load(input);
                for (java.util.Map.Entry<?, ?> entry : devices.entrySet()) if (entry.getKey().toString().equalsIgnoreCase(code)) {
                    String version = String.valueOf(((java.util.Map<?, ?>)entry.getValue()).get("platver"));
                    if (version.startsWith("epoc6") || version.startsWith("epoc7") || version.startsWith("epoc8")) return QD;
                }
            }
        } catch (Exception ignored) {}
        if (value.contains("rm-99") || value.contains("n70")) return QD;
        return N73;
    }

    public PhoneModel oriented(boolean landscape) {
        if (this == QD) return QD;
        if (this == N8 || this == N8_LANDSCAPE) return landscape ? N8_LANDSCAPE : N8;
        if (this == N86 || this == N86_MEDIA) return landscape ? N86_MEDIA : N86;
        return landscape ? N86_MEDIA : N73;
    }

    public PhoneModel family(){return this == N8_LANDSCAPE ? N8 : this == N86 || this == N86_MEDIA ? N73 : this;}

    public static void applyOrientation(android.app.Activity activity){
        com.github.eka2l1.settings.AppDataStore store=com.github.eka2l1.settings.AppDataStore.getAndroidStore();
        PhoneModel family=parse(store.getString(PREF,"N73")).family();
        String mode=store.getString("phone-orientation","auto");
        boolean landscape=family==QD||mode.equals("landscape");
        activity.setRequestedOrientation(landscape
            ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    public int findDevice(String[] names, String[] codes, boolean[] touch) {
        PhoneModel wanted=family();
        if (this == N73) for (int i = 0; i < names.length; i++)
            if (codes[i].equalsIgnoreCase("RM-409") || names[i].contains("N73")) return i;
        for (int i = 0; i < names.length; i++) {
            PhoneModel candidate = forDevice(names[i], codes[i], touch[i]);
            if (candidate == wanted) return i;
        }
        // N73 is the visual shell for the installed S60V3 devices, including 5320.
        return -1;
    }
}
