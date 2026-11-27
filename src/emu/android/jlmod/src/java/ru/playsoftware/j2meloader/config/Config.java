/*
 * Copyright 2018-2019 Nikita Shakarun
 * Copyright 2020-2026 Yury Kharchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.playsoftware.j2meloader.config;

import static ru.playsoftware.j2meloader.util.Constants.ACTION_EDIT;
import static ru.playsoftware.j2meloader.util.Constants.KEY_MIDLET_NAME;
import static ru.playsoftware.j2meloader.util.Constants.PREF_EMULATOR_DIR;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;

import androidx.annotation.Keep;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.R;
import ru.woesss.j2me.jar.Descriptor;

public class Config {
	public static final String APPS_DB_NAME = "/J2ME-apps.db";
	public static final String DEX_OPT_CACHE_DIR = "dex_opt";
	public static final String MIDLET_CONFIGS_DIR = "/configs/";
	public static final String MIDLET_CONFIG_FILE = "/config.json";
	public static final String MIDLET_DATA_DIR = "/data/";
	public static final String MIDLET_DEX_ARCH = "/converted.zip";
	public static final String MIDLET_DEX_FILE = "/converted.dex";
	public static final String MIDLET_ICON_FILE = "/icon.png";
	public static final String MIDLET_KEY_LAYOUT_FILE = "/VirtualKeyboardLayout";
	public static final String MIDLET_MANIFEST_FILE = MIDLET_DEX_FILE + ".conf";
	public static final String MIDLET_RES_DIR = "/res";
	public static final String MIDLET_RES_FILE = "/res.jar";
	public static final String SCREENSHOTS_DIR;
	public static final String SHADERS_DIR = "/shaders/";
	public static final String SKINS_DIR = "/skins/";
	public static final String SOUNDBANKS_DIR = "/soundbanks/";

	private static String emulatorDir;
	private static String dataDir;
	private static String configsDir;
	private static String profilesDir;
	private static String appDir;

	@Keep
	private static final SharedPreferences.OnSharedPreferenceChangeListener sPrefListener =
			(sharedPreferences, key) -> {
				if (PREF_EMULATOR_DIR.equals(key)) {
					initDirs(sharedPreferences.getString(key, emulatorDir));
				}
			};

	static {
		Context context = ContextHolder.getAppContext();
		String appName = context.getString(R.string.jlmod_jlmod_app_name);
		SCREENSHOTS_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
				+ "/" + appName;
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		String path = preferences.getString(PREF_EMULATOR_DIR, null);
		if (path == null) path = Environment.getExternalStorageDirectory() + "/" + appName;
		initDirs(path);
		preferences.registerOnSharedPreferenceChangeListener(sPrefListener);
	}

	public static String getEmulatorDir() {
		return emulatorDir;
	}

	public static String getDataDir() {
		return dataDir;
	}

	public static String getConfigsDir() {
		return configsDir;
	}

	public static String getProfilesDir() {
		return profilesDir;
	}

	public static String getAppDir() {
		return appDir;
	}

	public static void openSettings(Context context, String name, String path) {
		Intent intent = new Intent(ACTION_EDIT, Uri.parse(path), context, ConfigActivity.class);
		intent.putExtra(KEY_MIDLET_NAME, name);
		context.startActivity(intent);
	}

	public static void startApp(Context context, String name, String path) {
		File appDir = new File(path);
		File convertedDir = appDir.getParentFile();
		File workDir = convertedDir == null ? null : convertedDir.getParentFile();
		File configDir = workDir == null ? null : new File(workDir, "configs/" + appDir.getName());
		File configFile = configDir == null ? null : new File(configDir, "config.json");
		if (configFile == null || !configFile.isFile()) {
			ProfileModel defaults = new ProfileModel(configDir);
			setNativeDisplaySize(defaults, appDir, name);
			// A new game starts with its declared MIDlet display dimensions, or the
			// selected handset's native LCD size when the JAR has no size metadata.
			if (configDir != null && (configDir.isDirectory() || configDir.mkdirs())
					&& ProfilesManager.saveConfig(defaults)) {
				// Config is ready; continue to the runtime below.
			} else {
				openSettings(context, name, path);
				return;
			}
		}

		Intent intent = new Intent(Intent.ACTION_DEFAULT, Uri.parse(path), context, MicroActivity.class);
		intent.putExtra(KEY_MIDLET_NAME, name);
		context.startActivity(intent);
	}

	private static void setNativeDisplaySize(ProfileModel profile, File appDir, String appName) {
		int[] size = null;
		try {
			Descriptor descriptor = new Descriptor(new File(appDir, MIDLET_MANIFEST_FILE), false);
			Map<String, String> attributes = descriptor.getAttrs();
			String[] names = {"Nokia-MIDlet-Original-Display-Size", "Nokia-MIDlet-Target-Display-Size",
					"MIDlet-Display-Size", "MIDlet-Screen-Size", "Display-Size"};
			for (String name : names) {
				String value = attributes.get(name);
				if (value != null && (size = parseDisplaySize(value)) != null) break;
			}
		} catch (Exception ignored) {
		}
		if (size == null && appName != null) size = parseDisplaySize(appName);
		if (size == null) {
			try {
				Class<?> phoneUi = Class.forName("com.github.eka2l1.phone.PhoneUi");
				size = (int[]) phoneUi.getMethod("getDefaultJavaDisplaySize").invoke(null);
			} catch (ReflectiveOperationException | LinkageError ignored) {
				size = new int[]{240, 320};
			}
		}
		profile.screenWidth = size[0];
		profile.screenHeight = size[1];
	}

	private static int[] parseDisplaySize(String value) {
		Matcher matcher = Pattern.compile("(\\d{2,4})\\s*[xX×,]\\s*(\\d{2,4})").matcher(value);
		if (!matcher.find()) return null;
		try {
			int width = Integer.parseInt(matcher.group(1));
			int height = Integer.parseInt(matcher.group(2));
			return width >= 64 && height >= 64 && width <= 2048 && height <= 2048
					? new int[]{width, height} : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static void initDirs(String path) {
		emulatorDir = path;
		dataDir = emulatorDir + MIDLET_DATA_DIR;
		configsDir = emulatorDir + MIDLET_CONFIGS_DIR;
		profilesDir = emulatorDir + "/templates/";
		appDir = emulatorDir + "/converted/";
	}
}
