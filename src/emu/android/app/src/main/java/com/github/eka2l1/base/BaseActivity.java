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

package com.github.eka2l1.base;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.eka2l1.R;
import com.github.eka2l1.settings.AppDataStore;

import static com.github.eka2l1.emu.Constants.PREF_THEME;

@SuppressLint("Registered")
public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppDataStore dataStore = AppDataStore.getAndroidStore();
        String theme = dataStore.getString(PREF_THEME, "light");
        if (theme.equals("dark")) {
            setTheme(R.style.AppTheme_Phone);
        } else {
            setTheme(R.style.AppTheme_Light_Phone);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setElevation(getResources().getDisplayMetrics().density * 2);
        }
        super.onCreate(savedInstanceState);
        com.github.eka2l1.phone.PhoneUi.useDisplayCutout(this);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        com.github.eka2l1.phone.PhoneModel.applyOrientation(this);
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks(){
            @Override public void onFragmentStarted(androidx.fragment.app.FragmentManager manager,androidx.fragment.app.Fragment fragment){
                if(fragment instanceof androidx.fragment.app.DialogFragment){android.app.Dialog dialog=((androidx.fragment.app.DialogFragment)fragment).getDialog();if(dialog!=null){com.github.eka2l1.phone.PhoneDialogs.prepare(dialog);com.github.eka2l1.phone.PhoneDialogs.fit(dialog);}}
                if(fragment.getView()!=null)com.github.eka2l1.phone.PhoneDialogs.containInputs(fragment.getView());
            }
        },true);
    }
    @Override protected void onResume() {
        super.onResume();
        com.github.eka2l1.phone.PhoneUi.refreshShell(this);
        com.github.eka2l1.phone.PhoneModel.applyOrientation(this);
    }

    @Override public void setContentView(int layout) {
        android.view.View content=getLayoutInflater().inflate(layout,null);
        super.setContentView(com.github.eka2l1.phone.PhoneUi.wrap(this,content,true));
    }
    @Override public void openOptionsMenu(){com.github.eka2l1.phone.PhoneUi.showOptions(this);}
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus)getWindow().getDecorView().setSystemUiVisibility(5894);}
}
