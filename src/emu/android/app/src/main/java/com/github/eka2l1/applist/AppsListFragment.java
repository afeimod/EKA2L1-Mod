/*
 * Copyright (c) 2020 EKA2L1 Team
 *
 * This file is part of EKA2L1 project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.eka2l1.applist;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.eka2l1.R;
import com.github.eka2l1.config.ConfigFragment;
import com.github.eka2l1.config.ProfilesFragment;
import com.github.eka2l1.emu.Emulator;
import com.github.eka2l1.emu.EmulatorActivity;
import com.github.eka2l1.phone.PhoneModel;
import com.github.eka2l1.phone.PhoneShellLayout;
import com.github.eka2l1.phone.PhoneHomeScreen;
import com.github.eka2l1.info.AboutDialogFragment;
import com.github.eka2l1.settings.AppDataStore;
import com.github.eka2l1.settings.SettingsFragment;
import com.github.eka2l1.util.AppUtils;
import com.github.eka2l1.util.FileUtils;
import com.github.eka2l1.util.LogUtils;
import com.github.eka2l1.util.ZipUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

import static com.github.eka2l1.emu.Constants.*;

import org.w3c.dom.Text;

public class AppsListFragment extends Fragment {
    private CompositeDisposable compositeDisposable;
    private AppsListAdapter adapter;
    private RecyclerView rvApplist;
    private TextView tvEmptyList;
    private DividerItemDecoration dividerItemDecoration;
    private boolean restartNeeded;
    private AppItem pendingAppItem;
    private PhoneShellLayout phoneShell;
    private PhoneHomeScreen phoneScreen;
    private TextView phonePicker;
    private PhoneModel selectedShell;
    private ProgressDialog appsLoadingDialog;
    private final ActivityResultLauncher<String[]> openSisLauncher = registerForActivityResult(
            FileUtils.getFilePicker(),
            this::onSisResult);
    private final ActivityResultLauncher<String[]> openJavaLauncher = registerForActivityResult(
            FileUtils.getFilePicker(),
            this::onJavaResult);
    private final ActivityResultLauncher<Void> openSDCardLauncher = registerForActivityResult(
            FileUtils.getNativeDirPicker(),
            this::onSDCardResult);
    private final ActivityResultLauncher<Void> openNGageGameLauncher = registerForActivityResult(
            FileUtils.getNativeDirPicker(),
            this::onNGageGameResult
    );
    private final ActivityResultLauncher<String[]> openPreconfiguredPackZIPLauncher = registerForActivityResult(
            FileUtils.getFilePicker(),
            this::onPreconfiguredPackZIPResult);
    private boolean filePickerInProgress;
    private final ActivityResultLauncher pickLaunchFileDirectory = registerForActivityResult(
            FileUtils.getDirPicker(true),
            this::onLaunchFileDirPickResult);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        filePickerInProgress = savedInstanceState != null
                && savedInstanceState.getBoolean("filePickerInProgress", false);
        adapter = new AppsListAdapter(getContext());
        compositeDisposable = new CompositeDisposable();
        getParentFragmentManager().setFragmentResultListener(KEY_RESTART, this, (key, bundle) -> {
            restartNeeded = true;
        });
        getParentFragmentManager().setFragmentResultListener(KEY_RESTART_IMMEDIATELY, this, (key, bundle) -> {
            restart();
        });

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("filePickerInProgress", filePickerInProgress);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_appslist, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvApplist = view.findViewById(R.id.rv_applist);
        tvEmptyList = view.findViewById(R.id.tv_empty_list);
        phoneShell = requireActivity().findViewById(R.id.phone_shell);
        phoneScreen = view.findViewById(R.id.phone_home_screen);
        phonePicker = view.findViewById(R.id.phone_model_picker);
        selectedShell = PhoneModel.parse(AppDataStore.getAndroidStore().getString(PhoneModel.PREF, "N73"));
        phoneShell.setModel(selectedShell);
        phoneScreen.setAdapter(adapter);
        phoneScreen.setOptionsAction(() -> requireActivity().openOptionsMenu());
        phoneScreen.setContextAction(()->{
            androidx.appcompat.view.menu.MenuBuilder menu=new androidx.appcompat.view.menu.MenuBuilder(requireContext());
            requireActivity().getMenuInflater().inflate(R.menu.context_appslist,menu);
            com.github.eka2l1.phone.PhoneUi.showMenu((AppCompatActivity)requireActivity(),menu,this::onContextItemSelected);
        });
        phoneShell.setKeyListener((code,down)->{
            if(isResumed()&&isVisible())phoneScreen.key(code,down);
            else com.github.eka2l1.phone.PhoneUi.key((AppCompatActivity)requireActivity(),code,down);
        });
        phonePicker.setOnClickListener(v -> choosePhone());
        registerForContextMenu(phoneScreen);
        updatePhone(false, "正在读取系统…");

        registerForContextMenu(rvApplist);

        rvApplist.setAdapter(adapter);
        setDisplay(view, true);

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                boolean empty = adapter.getItemCount() == 0;

                rvApplist.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
                tvEmptyList.setVisibility(empty ? View.VISIBLE : View.INVISIBLE);
            }
        });

        setHasOptionsMenu(true);
        prepareApps();
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setTitle(R.string.app_name);
        View fab = view.findViewById(R.id.fab);
        fab.setOnClickListener(v -> chooseInstallType());
    }

    private void updatePhone(boolean available, String device) {
        phoneShell.setModel(selectedShell);
        phonePicker.setText(selectedShell.title + " · " + selectedShell.platform + "  ▾");
        phoneScreen.setDevice(selectedShell, device, available);
    }

    private void choosePhone() {
        PhoneModel[] models = {PhoneModel.N73,PhoneModel.QD,PhoneModel.N8};
        String[] labels = new String[models.length];
        for (int i = 0; i < models.length; i++) labels[i] = models[i].title + " · " + models[i].platform;
        new com.github.eka2l1.phone.PhoneDialogs.PlatformBuilder(requireContext()).setTitle("选择机型")
                .setSingleChoiceItems(labels, java.util.Arrays.asList(models).indexOf(selectedShell.family()), (dialog, index) -> {
                    dialog.dismiss(); selectedShell = models[index];
                    AppDataStore store = AppDataStore.getAndroidStore();
                    store.putString(PhoneModel.PREF, selectedShell.name()); store.save();
                    PhoneModel.applyOrientation(requireActivity());
                    if (!Emulator.isInitialized()) { updatePhone(false, "系统未安装"); return; }
                    String[] names = Emulator.getDevices(), codes = Emulator.getDeviceFirmwareCodes();
                    boolean[] touch = new boolean[names.length];
                    for (int i = 0; i < names.length; i++) touch[i] = Emulator.isTouchScreenDevice(codes[i]);
                    int device = selectedShell.findDevice(names, codes, touch);
                    if (device < 0) { updatePhone(false, "系统未安装");
                        new com.github.eka2l1.phone.PhoneDialogs.PlatformBuilder(requireContext()).setTitle("对应系统未安装")
                                .setMessage("外观已切换。导入该机型的 ROM / RPKG 后即可显示和运行它的软件。")
                                .setPositiveButton("导入固件", (d, w) -> switchToDeviceList())
                                .setNegativeButton("稍后", null).show(); return; }
                    if (device != Emulator.getCurrentDevice()) { Emulator.setCurrentDevice(device, false); restart(); }
                    else updatePhone(true, names[device]);
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void syncPhoneToDevice() {
        String[] names = Emulator.getDevices(), codes = Emulator.getDeviceFirmwareCodes();
        int id = Emulator.getCurrentDevice();
        if (id < 0 || id >= names.length) { updatePhone(false, "系统未安装"); return; }
        PhoneModel actual = PhoneModel.forDevice(names[id], codes[id], Emulator.isTouchScreenDevice(codes[id]));
        boolean same = actual == selectedShell.family();
        boolean[] touch = new boolean[names.length];
        for (int i = 0; i < names.length; i++) touch[i] = Emulator.isTouchScreenDevice(codes[i]);
        if (!same && selectedShell.findDevice(names, codes, touch) < 0) { updatePhone(false, "系统未安装"); return; }
        if (!same) { selectedShell = actual; AppDataStore store = AppDataStore.getAndroidStore(); store.putString(PhoneModel.PREF, actual.name()); store.save(); }
        updatePhone(true, names[id]);
    }

    private boolean selectDefaultDevice() {
        AppDataStore store = AppDataStore.getAndroidStore();
        if (store.getString(PhoneModel.PREF, null) != null) return false;
        store.putString(PhoneModel.PREF, "N73"); store.save();
        String[] names = Emulator.getDevices(), codes = Emulator.getDeviceFirmwareCodes();
        int preferred = -1;
        for (int i = 0; i < names.length; i++) {
            if (codes[i].equalsIgnoreCase("RM-409") || names[i].contains("N73")) { preferred = i; break; }
        }
        if (preferred >= 0 && preferred != Emulator.getCurrentDevice()) {
            Emulator.setCurrentDevice(preferred, false); restart(); return true;
        }
        return false;
    }

    private void setDisplay(View view, boolean isGrid) {
        if (view == null) {
            view = getView();
        }

        Context context = view.getContext();

        rvApplist.setPadding(0, 0, 0, isGrid ? 200 : 0);
        rvApplist.requestLayout();

        if (isGrid) {
            rvApplist.setLayoutManager(new GridLayoutManager(context, 4));
            if (dividerItemDecoration != null) {
                rvApplist.removeItemDecoration(dividerItemDecoration);
            }
        } else {
            if (dividerItemDecoration == null) {
                dividerItemDecoration = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
            }

            rvApplist.setLayoutManager(new LinearLayoutManager(context));
            rvApplist.addItemDecoration(dividerItemDecoration);
        }

        adapter.setDisplay(isGrid);
    }

    private void switchToDeviceList() {
        DeviceListFragment deviceListFragment = new DeviceListFragment();
        getParentFragmentManager().beginTransaction()
                .replace(R.id.container, deviceListFragment)
                .addToBackStack(null)
                .commit();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void prepareApps() {
        ProgressDialog dialog = new com.github.eka2l1.phone.PhoneDialogs.Progress(getActivity());
        appsLoadingDialog = dialog;
        dialog.setIndeterminate(true);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCancelable(false);
        dialog.setMessage(getText(R.string.processing));
        dialog.show();
        Single<ArrayList<AppItem>> appsSingle = Emulator.getAppsList();
        compositeDisposable.add(appsSingle.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableSingleObserver<ArrayList<AppItem>>() {
                    @Override
                    public void onSuccess(@NonNull ArrayList<AppItem> appItems) {
                        if (!isAdded() || getView() == null) return;
                        if (selectDefaultDevice()) { dialog.cancel(); return; }
                        appItems.addAll(JavaAppCatalog.readInstalledApps());
                        adapter.setItems(appItems);
                        syncPhoneToDevice();
                        dialog.cancel();
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        if (!isAdded() || getView() == null) return;
                        adapter.setItems(new ArrayList<>(JavaAppCatalog.readInstalledApps()));
                        Toast.makeText(getContext(), R.string.no_devices_found, Toast.LENGTH_SHORT).show();
                        dialog.cancel();

                        AlertDialog installDeviceAskDialog = new com.github.eka2l1.phone.PhoneDialogs.PlatformBuilder(getActivity())
                                .setTitle(R.string.no_device_dialog_title)
                                .setMessage(R.string.no_device_installed)
                                .setPositiveButton(R.string.install, (d, id) -> {
                                    d.cancel();

                                    new com.github.eka2l1.phone.PhoneDialogs.PlatformBuilder(getActivity())
                                            .setTitle(R.string.choose_installation_type)
                                            .setMessage(R.string.choose_installation_type_instruction)
                                            .setPositiveButton(R.string.rom_or_firmware, (d2, id2) -> {
                                                d2.cancel();
                                                switchToDeviceList();
                                            })
                                            .setNegativeButton(R.string.preconfigured_pack, (d2, i2) -> {
                                                d2.cancel();
                                                openPreconfiguredZIPFilePicker();
                                            })
                                            .show();
                                }).setNegativeButton(android.R.string.cancel, (d, id) -> d.cancel()).create();
                        installDeviceAskDialog.show();
                    }
                }));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (restartNeeded) {
            restart();
        }
    }

    private void restart() {
        AppUtils.restart(requireContext());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        compositeDisposable.clear();
    }

    @Override public void onDestroyView() {
        compositeDisposable.clear();
        if (appsLoadingDialog != null) {
            if (appsLoadingDialog.isShowing()) appsLoadingDialog.dismiss();
            appsLoadingDialog = null;
        }
        if (phoneShell != null) phoneShell.releaseKeys();
        if (phoneScreen != null) phoneScreen.setAdapter(null);
        super.onDestroyView();
    }

    private void onSisResult(String path) {
        filePickerInProgress = false;
        if (!Emulator.isInitialized()) {
            Toast.makeText(getContext(), R.string.error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (path == null) {
            return;
        }
        installApp(path);
    }

    private void launchFilePicker(ActivityResultLauncher<String[]> launcher, String[] extensions) {
        if (filePickerInProgress) {
            return;
        }
        filePickerInProgress = true;
        try {
            launcher.launch(extensions);
        } catch (RuntimeException e) {
            filePickerInProgress = false;
            throw e;
        }
    }

    private void chooseInstallType() {
        new com.github.eka2l1.phone.PhoneDialogs.PlatformBuilder(requireContext())
                .setTitle("安装软件")
                .setItems(new String[]{"Symbian 软件（SIS/SISX）", "Java 软件（JAR/JAD）"}, (dialog, which) -> {
                    if (which == 0) launchFilePicker(openSisLauncher, new String[]{".sis", ".sisx"});
                    else launchFilePicker(openJavaLauncher, new String[]{".jar", ".jad"});
                }).show();
    }

    private void onJavaResult(String path) {
        filePickerInProgress = false;
        if (path == null || !isAdded()) return;
        try {
            // JL-Mod stores its database and converted MIDlets inside EKA2L1's working
            // directory, so the Java catalog remains available in the same app list.
            String workDir = JavaAppCatalog.getWorkDirectory();
            File directory = new File(workDir);
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Cannot create Java ME work directory");
            }
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit().putString(ru.playsoftware.j2meloader.util.Constants.PREF_EMULATOR_DIR, workDir)
                    .apply();
            ViewModelProvider provider = new ViewModelProvider(requireActivity());
            ru.playsoftware.j2meloader.applist.AppListModel model =
                    provider.get(ru.playsoftware.j2meloader.applist.AppListModel.class);
            model.setEmulatorDirectory(workDir);
            Uri fileUri = path.startsWith("/") ? Uri.fromFile(new File(path)) : Uri.parse(path);
            getParentFragmentManager().setFragmentResultListener("jlmod-install-result", this,
                    (key, result) -> {
                        prepareApps();
                    });
            ru.woesss.j2me.installer.InstallerDialog.newInstance(fileUri)
                    .show(getParentFragmentManager(), "jlmod-installer");
        } catch (Exception e) {
            Log.e("AppsListFragment", "Unable to open Java ME installer", e);
            Toast.makeText(requireContext(), "无法启动 Java 安装器：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void onSDCardResult(String path) {
        if (!Emulator.isInitialized()) {
            Toast.makeText(getContext(), R.string.error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (path == null) {
            return;
        }
        mountSdCard(path);
    }

    private void onNGageGameResult(String path) {
        if (!Emulator.isInitialized()) {
            Toast.makeText(getContext(), R.string.error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (path == null) {
            return;
        }
        installNGageGame(path);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void installApp(String path) {
        Completable completable = Emulator.subscribeInstallApp(path);
        completable.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableCompletableObserver() {
                    @Override
                    public void onComplete() {
                        Toast.makeText(getContext(), R.string.completed, Toast.LENGTH_SHORT).show();
                        restart();
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        Toast.makeText(getContext(), R.string.error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void mountSdCard(String path) {
        if (path == null) {
            return;
        }

        String folderName = FileUtils.getFilenameFromPath(getContext(), path);

        Pattern cidPattern = Pattern.compile("[0-9a-fA-F]+-[0-9a-fA-F]+-[0-9a-fA-F]+-[0-9a-fA-F]+");
        Matcher matcher = cidPattern.matcher(folderName);

        boolean mmcIdFoundInFolderName = matcher.find();

        Emulator.mountSdCard(path);

        if (mmcIdFoundInFolderName) {
            Emulator.setCurrentMMCID(matcher.group());
            Toast.makeText(getContext(), R.string.mounted_mmc_ok_and_use_id_in_folder_name, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), R.string.completed, Toast.LENGTH_SHORT).show();
        }

        prepareApps();
    }

    private void installNGageGame(String path) {
        int result = Emulator.installNGageGame(path);
        if (result == 0) {
            Toast.makeText(getContext(), R.string.completed, Toast.LENGTH_SHORT).show();
            restart();
        } else {
            int errorResId = R.string.error;
            switch (result) {
                case Emulator.INSTALL_NG_GAME_ERROR_NO_GAME_DATA_FOLDER:
                    errorResId = R.string.install_ng_game_cant_find_data_folder;
                    break;

                case Emulator.INSTALL_NG_GAME_ERROR_MORE_THAN_ONE_DATA_FOLDER:
                    errorResId = R.string.install_ng_game_more_than_one_game;
                    break;

                case Emulator.INSTALL_NG_GAME_ERROR_NO_GAME_REGISTERATION_INFO:
                    errorResId = R.string.install_ng_game_no_game_info_file;
                    break;

                case Emulator.INSTALL_NG_GAME_ERROR_REGISTERATION_CORRUPTED:
                    errorResId = R.string.install_ng_game_corrupted_game_info;
                    break;

                default:
                    break;
            }
            Toast.makeText(getContext(), errorResId, Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("CheckResult")
    private void extractPreconfiguredPack(String inputPath, String destPath, String destPathTemp) {
        InputStream sourceZipStream;

        try {
            sourceZipStream = getContext().getContentResolver().openInputStream(Uri.parse(inputPath));
        } catch (FileNotFoundException ex) {
            Toast.makeText(getContext(), R.string.fail_to_install_preconfigured_pack, Toast.LENGTH_LONG).show();
            return;
        }

        ProgressDialog dialog = new com.github.eka2l1.phone.PhoneDialogs.Progress(getActivity());
        dialog.setIndeterminate(true);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCancelable(false);
        dialog.setMessage(getText(R.string.processing));
        dialog.show();

        Single<Boolean> extractPackSingle = Single.create(emitter -> {
           try {
               ZipUtils.unzip(sourceZipStream, new File(destPathTemp));

               if (destPath != null) {
                    File destRealFile = new File(destPath);
                    FileUtils.deleteDirectory(destRealFile);

                    new File(destPathTemp + "/data").renameTo(destRealFile);
                    FileUtils.deleteDirectory(new File(destPathTemp));
               }
           } catch (IOException io)
           {
               // Delete temporary folder
               FileUtils.deleteDirectory(new File(destPathTemp));
               emitter.onError(io);
               return;
           }

           emitter.onSuccess(true);
        });

        extractPackSingle.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableSingleObserver<Boolean>() {
                    @SuppressLint("CheckResult")
                    @Override
                    public void onSuccess(@NonNull Boolean result) {
                        Toast.makeText(getContext(), R.string.completed, Toast.LENGTH_SHORT).show();
                        dialog.cancel();

                        restart();
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        Toast.makeText(getContext(), (destPath != null) ? R.string.fail_to_install_preconfigured_pack_and_rollback : R.string.fail_to_install_preconfigured_pack, Toast.LENGTH_LONG).show();
                        dialog.cancel();
                    }
                });
    }

    private void onPreconfiguredPackZIPResult(String sourcePath) {
        filePickerInProgress = false;
        if (sourcePath == null) {
            return;
        }

        String dataFolderPath = Emulator.getEmulatorDir() + "/data";
        String dataFolderPathTemp = Emulator.getEmulatorDir() + "/data_temp";

        File dataFolder = new File(dataFolderPath);

        if (dataFolder.exists()) {
            new com.github.eka2l1.phone.PhoneDialogs.PlatformBuilder(getContext())
                    .setTitle(R.string.delete_existing_data_title)
                    .setMessage(R.string.delete_existing_data)
                    .setPositiveButton(android.R.string.yes, (d, i) -> {
                        extractPreconfiguredPack(sourcePath, dataFolderPath, dataFolderPathTemp);
                    })
                    .setNegativeButton(android.R.string.cancel, (d, i) -> {
                        Toast.makeText(getContext(), R.string.cancelled, Toast.LENGTH_SHORT).show();
                    })
                    .show();
        } else {
            extractPreconfiguredPack(sourcePath, null, Emulator.getEmulatorDir());
        }
    }

    private void openPreconfiguredZIPFilePicker() {
        launchFilePicker(openPreconfiguredPackZIPLauncher, new String[] { "*.zip" });
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.main, menu);
        menu.add(0,R.id.action_phone_family,0,"机型系列");
        menu.add(0,R.id.action_phone_orientation,1,"屏幕方向");
        menu.add(0,R.id.action_phone_install,2,"安装软件");
        menu.add(0,R.id.action_phone_keys,3,"机身按键映射");
        final MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        Disposable searchViewDisposable = Observable.create((ObservableOnSubscribe<String>) emitter -> {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    emitter.onNext(query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    emitter.onNext(newText);
                    return true;
                }
            });
        }).debounce(300, TimeUnit.MILLISECONDS)
                .map(String::toLowerCase)
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(charSequence -> adapter.getFilter().filter(charSequence));
        compositeDisposable.add(searchViewDisposable);

        menu.removeItem(R.id.action_toggle_grid);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_phone_family) {choosePhone();return true;
        } else if (itemId == R.id.action_phone_orientation) {com.github.eka2l1.phone.PhoneUi.chooseOrientation(requireActivity());return true;
        } else if (itemId == R.id.action_phone_keys) {startActivity(new Intent(requireContext(), com.github.eka2l1.phone.PhoneKeyMapActivity.class));return true;
        } else if (itemId == R.id.action_phone_install) {chooseInstallType();return true;
        } else if (itemId == R.id.action_search) {
            com.github.eka2l1.phone.PhoneDialogs.askText(requireActivity(),"搜索软件","",value->adapter.getFilter().filter(value.toLowerCase()));return true;
        } else if (itemId == R.id.action_about) {
            AboutDialogFragment aboutDialogFragment = new AboutDialogFragment();
            aboutDialogFragment.show(getParentFragmentManager(), "about");
        } else if (itemId == R.id.action_settings) {
            SettingsFragment settingsFragment = new SettingsFragment();
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.container, settingsFragment)
                    .addToBackStack(null)
                    .commit();
        } else if (itemId == R.id.action_packages) {
            PackageListFragment packageListFragment = new PackageListFragment();
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.container, packageListFragment)
                    .addToBackStack(null)
                    .commit();
        } else if (itemId == R.id.action_devices) {
            switchToDeviceList();
        } else if (itemId == R.id.action_mount_sd) {
            openSDCardLauncher.launch(null);
        } else if (itemId == R.id.action_save_log) {
            try {
                LogUtils.writeLog();
                Toast.makeText(getActivity(), R.string.log_saved, Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
            }
        } else if (itemId == R.id.action_profiles) {
            ProfilesFragment profilesFragment = new ProfilesFragment();
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.container, profilesFragment)
                    .addToBackStack(null)
                    .commit();
        } else if (itemId == R.id.action_install_ng_game) {
            openNGageGameLauncher.launch(null);
        } else if (itemId == R.id.action_install_preconfigured_pack) {
            openPreconfiguredZIPFilePicker();
            return true;
        } else if (itemId == R.id.action_switch_devices) {
            SwitchDevicesAlert.newInstance()
                    .show(getParentFragmentManager(), "alert_switch_devices");
        }
        return super.onOptionsItemSelected(item);
    }

    private void addAppShortcut(AppItem item) {
        // Get current device code
        int currentDeviceId = Emulator.getCurrentDevice();
        String []deviceCode = Emulator.getDeviceFirmwareCodes();

        if (currentDeviceId >= deviceCode.length) {
            return;
        }

        Bitmap iconBitmap = item.getIcon();
        IconCompat icon = null;

        if (iconBitmap == null) {
            icon = IconCompat.createWithResource(getActivity(), R.mipmap.ic_ducky_foreground);
        } else {
            int width = iconBitmap.getWidth();
            int height = iconBitmap.getHeight();
            ActivityManager am = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
            int iconSize = am.getLauncherLargeIconSize();
            Rect src;
            if (width > height) {
                int left = (width - height) / 2;
                src = new Rect(left, 0, left + height, height);
            } else if (width < height) {
                int top = (height - width) / 2;
                src = new Rect(0, top, width, top + width);
            } else {
                src = null;
            }
            Bitmap scaled = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(scaled);
            canvas.drawBitmap(iconBitmap, src, new RectF(0, 0, iconSize, iconSize), null);
            icon = IconCompat.createWithBitmap(scaled);
        }

        Intent launchIntent = new Intent(Intent.ACTION_DEFAULT, null, getActivity(), EmulatorActivity.class);
        launchIntent.putExtra(KEY_APP_UID, item.getUid());
        launchIntent.putExtra(KEY_APP_NAME, item.getTitle());
        launchIntent.putExtra(KEY_DEVICE_CODE, deviceCode[currentDeviceId]);
        launchIntent.putExtra(KEY_APP_IS_SHORTCUT, true);

        ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(getActivity(), item.getTitle())
                .setIntent(launchIntent)
                .setShortLabel(item.getTitle())
                .setIcon(icon)
                .build();

        try {
            ShortcutManagerCompat.requestPinShortcut(getActivity(), shortcut, null);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void saveAppLaunchFileToDirectory(String path, AppItem item) {
        int currentDeviceId = Emulator.getCurrentDevice();
        String []deviceCode = Emulator.getDeviceFirmwareCodes();

        AppLaunchInfo launchInfo = new AppLaunchInfo(item.getUid(), item.getTitle(), deviceCode[currentDeviceId]);
        String fileName = item.getTitle() + ".json";

        Uri filePath = Uri.parse(path);
        DocumentFile rootFile = DocumentFile.fromTreeUri(getContext(), filePath);
        DocumentFile jsonFile = rootFile.findFile(fileName);

        if (jsonFile == null) {
            jsonFile = rootFile.createFile("application/json", fileName);
        }

        filePath = jsonFile.getUri();

        try (OutputStream outputStream = getActivity().getContentResolver().openOutputStream(filePath, "wt")) {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            gson.toJson(launchInfo, outputStreamWriter);

            outputStreamWriter.flush();
            outputStreamWriter.close();

            Toast.makeText(getContext(), String.format(getString(R.string.save_launch_file_success), fileName),
                    Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void onLaunchFileDirPickResult(String newPath) {
        Uri persistableUri = Uri.parse(newPath);

        getActivity().getContentResolver().takePersistableUriPermission(persistableUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        AppDataStore dataStore = AppDataStore.getAndroidStore();

        dataStore.putString(PREF_LAUNCH_FILE_DIR, newPath);
        dataStore.save();

        saveAppLaunchFileToDirectory(newPath, pendingAppItem);
    }

    private void saveAppLaunchFile(AppItem item) {
        String launchFileDir = AppDataStore.getAndroidStore().getString(PREF_LAUNCH_FILE_DIR, null);
        if (launchFileDir == null) {
            pendingAppItem = item;
            pickLaunchFileDirectory.launch(null);
        } else {
            saveAppLaunchFileToDirectory(launchFileDir, item);
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        int index = AppItemViewHolder.contextIndex;
        AppItem appItem = adapter.getItem(index);
        if (item.getItemId() == R.id.action_context_settings) {
            if (appItem.isJavaApp()) {
                ru.playsoftware.j2meloader.config.Config.openSettings(
                        requireContext(), appItem.getTitle(), appItem.getJavaAppPath());
                return true;
            }
            ConfigFragment configFragment = new ConfigFragment();
            Bundle args = new Bundle();
            args.putLong(KEY_APP_UID, appItem.getUid());
            args.putString(KEY_APP_NAME, appItem.getTitle());
            args.putString(KEY_ACTION, ACTION_EDIT);
            configFragment.setArguments(args);
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.container, configFragment)
                    .addToBackStack(null)
                    .commit();
        } else if (item.getItemId() == R.id.action_context_shortcut) {
            addAppShortcut(appItem);
        } else if (item.getItemId() == R.id.action_context_launch_file) {
            saveAppLaunchFile(appItem);
        }
        return super.onContextItemSelected(item);
    }
}
