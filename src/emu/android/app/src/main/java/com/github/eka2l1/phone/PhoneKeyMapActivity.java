package com.github.eka2l1.phone;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.github.eka2l1.R;
import com.github.eka2l1.base.BaseActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

/** Edits mappings for every key on each available handset shell. */
public class PhoneKeyMapActivity extends BaseActivity {
    private static final String[] ASSETS = {"n73", "n86", "n8", "qd"};
    private static final String[] TITLES = {"Nokia N73", "Nokia N86", "Nokia N8", "N-Gage QD"};
    private int selectedModel;
    private LinearLayout rows;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(10), dp(8), dp(10), dp(8));
        page.setBackgroundColor(0xffe8edf0);

        TextView title = new TextView(this);
        title.setText("机身按键映射");
        title.setTextColor(0xff17232b);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        page.addView(title, new LinearLayout.LayoutParams(-1, dp(40)));

        Spinner modelPicker = new Spinner(this);
        modelPicker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, TITLES));
        page.addView(modelPicker, new LinearLayout.LayoutParams(-1, dp(44)));
        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        modelPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedModel = position;
                populateRows();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        setContentView(PhoneUi.wrap(this, page, true));
    }

    private void populateRows() {
        rows.removeAllViews();
        String asset = ASSETS[selectedModel];
        try {
            JSONArray keys = readJson("phone/" + asset + ".json");
            for (int i = 0; i < keys.length(); i++) {
                JSONObject key = keys.getJSONObject(i);
                int defaultCode = key.getInt("code");
                String control = "key:" + i;
                int current = PhoneKeyMappings.get(asset, control, defaultCode, false);
                String label = "按键 " + (i + 1);
                if ("n86".equals(asset) && i >= 6 && i <= 9) label = "右侧按键 " + (i - 5);
                addRow(label, control, defaultCode, current);
            }
            JSONArray nav = readJson("phone/" + asset + "-nav.json");
            if (nav.length() > 0) {
                JSONObject data = nav.getJSONObject(0);
                addRow("摇杆 / 导航 · 上", "nav:up", KeycodeValue.UP, PhoneKeyMappings.get(asset, "nav:up", KeycodeValue.UP, false));
                addRow("摇杆 / 导航 · 下", "nav:down", KeycodeValue.DOWN, PhoneKeyMappings.get(asset, "nav:down", KeycodeValue.DOWN, false));
                addRow("摇杆 / 导航 · 左", "nav:left", KeycodeValue.LEFT, PhoneKeyMappings.get(asset, "nav:left", KeycodeValue.LEFT, false));
                addRow("摇杆 / 导航 · 右", "nav:right", KeycodeValue.RIGHT, PhoneKeyMappings.get(asset, "nav:right", KeycodeValue.RIGHT, false));
                if (data.optBoolean("confirm", false)) {
                    addRow("摇杆 / 导航 · 中键", "nav:center", KeycodeValue.FIRE,
                            PhoneKeyMappings.get(asset, "nav:center", KeycodeValue.FIRE, false));
                }
            }
        } catch (Exception e) {
            TextView error = rowText("读取机身按键失败：" + e.getMessage());
            rows.addView(error);
        }
    }

    private JSONArray readJson(String path) throws Exception {
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(path)))) {
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
        }
        if (path.endsWith("-nav.json")) {
            JSONObject nav = new JSONObject(json.toString());
            ArrayList<JSONObject> values = new ArrayList<>();
            if (nav.has("states")) values.add(nav);
            return new JSONArray(values);
        }
        return new JSONArray(json.toString());
    }

    private void addRow(String name, String control, int fallback, int current) {
        TextView row = rowText(name + "      " + PhoneKeyMappings.label(current));
        row.setBackgroundColor(0xfff8fafb);
        row.setOnClickListener(v -> editMapping(name, control, fallback, current));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(44));
        params.bottomMargin = dp(4);
        rows.addView(row, params);
    }

    private void editMapping(String name, String control, int fallback, int current) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("键值：0-9、A、B、*、#、上/下/左/右或 0xNN");
        input.setText(PhoneKeyMappings.label(current));
        new PhoneDialogs.SupportBuilder(this).setTitle(name)
                .setMessage("输入要发送到模拟器的键值。方向键也可输入十六进制键码。")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        PhoneKeyMappings.put(ASSETS[selectedModel], control, PhoneKeyMappings.parse(input.getText().toString()));
                        populateRows();
                    } catch (RuntimeException e) {
                        Toast.makeText(this, "键值无效", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("恢复默认", (dialog, which) -> {
                    PhoneKeyMappings.reset(ASSETS[selectedModel], control);
                    populateRows();
                })
                .setNegativeButton("取消", null).show();
    }

    private TextView rowText(String text) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(0xff17232b);
        row.setTextSize(14);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), 0, dp(10), 0);
        return row;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class KeycodeValue {
        static final int UP = 0x10, DOWN = 0x11, LEFT = 0x0e, RIGHT = 0x0f, FIRE = 0xa7;
    }
}
