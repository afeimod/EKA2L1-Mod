package com.github.eka2l1.applist;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.github.eka2l1.emu.Emulator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ru.playsoftware.j2meloader.config.Config;
import ru.woesss.j2me.jar.Descriptor;

/** Reads the MIDlets installed by the bundled JL-Mod runtime for EKA2L1's app list. */
final class JavaAppCatalog {
    private JavaAppCatalog() {}

    static String getWorkDirectory() {
        return Emulator.getEmulatorDir() + "jlmod/";
    }

    static List<AppItem> readInstalledApps() {
        ArrayList<AppItem> apps = new ArrayList<>();
        File root = new File(Config.getAppDir());
        File[] entries = root.listFiles(File::isDirectory);
        if (entries == null) return apps;
        for (File entry : entries) {
            File descriptorFile = new File(entry, Config.MIDLET_MANIFEST_FILE);
            if (!descriptorFile.isFile()) continue;
            try {
                Descriptor descriptor = new Descriptor(descriptorFile, false);
                String title = descriptor.getName();
                if (title == null || title.trim().isEmpty()) title = entry.getName();
                File iconFile = new File(entry, Config.MIDLET_ICON_FILE);
                Bitmap icon = iconFile.isFile() ? BitmapFactory.decodeFile(iconFile.getAbsolutePath()) : null;
                apps.add(AppItem.javaApp(title, icon, entry.getAbsolutePath()));
            } catch (Exception ignored) {
                // Ignore incomplete installs and let JL-Mod's own installer report malformed packages.
            }
        }
        return apps;
    }
}
