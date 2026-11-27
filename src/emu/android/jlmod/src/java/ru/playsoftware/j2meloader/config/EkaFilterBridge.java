package ru.playsoftware.j2meloader.config;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shares EKA2L1's global upscale-filter preference without coupling the JL-Mod
 * library module to EKA2L1 classes at compile time.
 */
public final class EkaFilterBridge {
	private static final String STORE_CLASS = "com.github.eka2l1.settings.AppDataStore";

	private EkaFilterBridge() {
	}

	public static boolean isAvailable() {
		try {
			Class.forName(STORE_CLASS);
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	public static boolean isEnabled() {
		return getString("screen-upscale-method", "0").equals("1");
	}

	public static String getShaderName() {
		String name = getString("filter-shader-path", "Default");
		return name == null || name.trim().isEmpty() ? "Default" : name;
	}

	public static void save(boolean enabled, String shaderName) {
		Object store = getStore();
		if (store == null) return;
		try {
			Class<?> type = store.getClass();
			type.getMethod("putString", String.class, String.class)
					.invoke(store, "screen-upscale-method", enabled ? "1" : "0");
			type.getMethod("putString", String.class, String.class)
					.invoke(store, "filter-shader-path",
							shaderName == null || shaderName.trim().isEmpty() ? "Default" : shaderName);
			type.getMethod("save").invoke(store);
		} catch (ReflectiveOperationException | LinkageError ignored) {
		}
	}

	public static List<String> getShaderNames() {
		ArrayList<String> names = new ArrayList<>();
		names.add("Default");
		File directory = getShaderDirectory();
		File[] files = directory == null ? null
				: directory.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".frag"));
		if (files != null) {
			Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
			for (File file : files) {
				String name = file.getName();
				names.add(name.substring(0, name.length() - 5));
			}
		}
		return names;
	}

	public static String getShaderSource(String shaderName) {
		if (shaderName == null || shaderName.trim().isEmpty() || shaderName.equals("Default")) return null;
		File directory = getShaderDirectory();
		if (directory == null) return null;
		File file = new File(directory, shaderName + ".frag");
		if (!file.isFile()) return null;
		try (java.io.FileInputStream input = new java.io.FileInputStream(file);
			 java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
			byte[] buffer = new byte[8192];
			int count;
			while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
			return output.toString("UTF-8");
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String getString(String key, String fallback) {
		Object store = getStore();
		if (store == null) return fallback;
		try {
			return (String) store.getClass()
					.getMethod("getString", String.class, String.class)
					.invoke(store, key, fallback);
		} catch (ReflectiveOperationException | LinkageError ignored) {
			return fallback;
		}
	}

	private static Object getStore() {
		try {
			Class<?> type = Class.forName(STORE_CLASS);
			return type.getMethod("getAndroidStore").invoke(null);
		} catch (ReflectiveOperationException | LinkageError ignored) {
			return null;
		}
	}

	private static File getShaderDirectory() {
		try {
			Class<?> emulatorClass = Class.forName("com.github.eka2l1.emu.Emulator");
			Method getEmulatorDir = emulatorClass.getMethod("getEmulatorDir");
			String root = (String) getEmulatorDir.invoke(null);
			return root == null ? null : new File(root, "resources/upscale");
		} catch (ReflectiveOperationException | LinkageError ignored) {
			return null;
		}
	}
}
