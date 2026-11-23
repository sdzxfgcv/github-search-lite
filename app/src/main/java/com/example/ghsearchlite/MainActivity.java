package com.example.ghsearchlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private EditText keywordEdit;
    private Spinner platformSpin, languageSpin, starSpin, daysSpin, sortSpin;
    private TextView statusText;
    private ListView listView;
    private ArrayAdapter<Repo> adapter;
    private final List<Repo> results = new ArrayList<>();
    private SharedPreferences prefs;

    private static final String[] LANGUAGES = {
            "语言: 全部", "语言: Kotlin", "语言: Java", "语言: Python", "语言: JavaScript",
            "语言: TypeScript", "语言: Go", "语言: Rust", "语言: C++", "语言: Swift", "语言: Dart"
    };
    private static final String[] STARS = {
            "Star: 不限", "Star: 100+", "Star: 500+", "Star: 1000+", "Star: 5000+"
    };
    private static final String[] DAYS = {
            "时间: 不限", "时间: 近一周", "时间: 近一月", "时间: 近一年"
    };
    private static final String[] SORTS = {
            "排序: 最佳匹配", "排序: 最多 Star", "排序: 最近更新"
    };
    private static final int[] STAR_VALUES = {0, 100, 500, 1000, 5000};
    private static final int[] DAY_VALUES = {0, 7, 30, 365};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("lite", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F5F7);

        TextView title = new TextView(this);
        title.setText("GitHub 搜索 Lite · 平台过滤");
        title.setTextSize(17);
        title.setTextColor(0xFF222222);
        title.setPadding(16, 16, 16, 8);
        root.addView(title);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(16, 4, 16, 4);
        keywordEdit = new EditText(this);
        keywordEdit.setHint("搜索仓库，如 android");
        keywordEdit.setSingleLine(true);
        keywordEdit.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row1.addView(keywordEdit);
        Button searchBtn = new Button(this);
        searchBtn.setText("搜索");
        searchBtn.setOnClickListener(v -> doSearch());
        row1.addView(searchBtn);
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(16, 2, 16, 2);
        platformSpin = makeSpinner(GitHubApi.PLATFORMS, "平台: ", 1);
        languageSpin = makeSpinner(LANGUAGES, "", 1);
        row2.addView(platformSpin);
        row2.addView(languageSpin);
        root.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setPadding(16, 2, 16, 2);
        starSpin = makeSpinner(STARS, "", 1);
        daysSpin = makeSpinner(DAYS, "", 1);
        sortSpin = makeSpinner(SORTS, "", 1);
        row3.addView(starSpin);
        row3.addView(daysSpin);
        row3.addView(sortSpin);
        root.addView(row3);

        statusText = new TextView(this);
        statusText.setText("提示：平台过滤会检查每个仓库最新稳定版 release 的资产后缀。频繁使用建议设置 Token。");
        statusText.setTextSize(11);
        statusText.setTextColor(0xFF777777);
        statusText.setPadding(16, 6, 16, 6);
        root.addView(statusText);

        listView = new ListView(this);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        adapter = new ArrayAdapter<Repo>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, results) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                Repo r = results.get(position);
                t1.setText(r.fullName);
                t1.setTextColor(0xFF2F6FED);
                String desc = r.description == null ? "" : r.description;
                if (desc.length() > 80) desc = desc.substring(0, 80) + "…";
                String meta = "★" + r.stars + "  " + (r.language == null ? "" : r.language)
                        + (r.platforms.isEmpty() ? "" : "  平台: " + r.platforms);
                t2.setText(desc + "\n" + meta);
                t2.setTextSize(11);
                return v;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Repo r = results.get(position);
            new AlertDialog.Builder(this)
                    .setTitle(r.fullName)
                    .setMessage(r.description)
                    .setPositiveButton("复制仓库网址", (d, w) -> copyUrl(r.htmlUrl))
                    .setNeutralButton("打开 GitHub", (d, w) -> openUrl(r.htmlUrl))
                    .setNegativeButton("取消", null)
                    .show();
        });
        root.addView(listView);

        Button tokenBtn = new Button(this);
        tokenBtn.setText("设置 Token（可选）");
        tokenBtn.setOnClickListener(v -> showTokenDialog());
        root.addView(tokenBtn);

        setContentView(root);
    }

    private Spinner makeSpinner(String[] items, String prefix, float weight) {
        String[] display = new String[items.length];
        for (int i = 0; i < items.length; i++) display[i] = prefix + items[i];
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, display);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        return s;
    }

    private void doSearch() {
        String platformRaw = (String) platformSpin.getSelectedItem();
        String platform = platformRaw.substring(platformRaw.indexOf(": ") + 2);
        String langRaw = (String) languageSpin.getSelectedItem();
        String language = langRaw.equals("语言: 全部")
                ? "" : langRaw.substring(langRaw.indexOf(": ") + 2);
        int minStars = STAR_VALUES[starSpin.getSelectedItemPosition()];
        int days = DAY_VALUES[daysSpin.getSelectedItemPosition()];
        int sortIdx = sortSpin.getSelectedItemPosition();
        String sort = sortIdx == 1 ? "stars" : (sortIdx == 2 ? "updated" : "best");
        String token = prefs.getString("token", "");

        statusText.setText("搜索中…");
        new Thread(() -> {
            try {
                List<Repo> found = GitHubApi.search(
                        keywordEdit.getText().toString(), language, minStars, days, sort, token);
                final List<Repo> finalList = GitHubApi.filterByPlatform(found, platform, token);
                runOnUiThread(() -> {
                    results.clear();
                    results.addAll(finalList);
                    adapter.notifyDataSetChanged();
                    statusText.setText("找到 " + results.size() + " 个仓库"
                            + ("全部".equals(platform) ? "" : "（平台：" + platform + "）"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("错误：" + e.getMessage()));
            }
        }).start();
    }

    private void copyUrl(String url) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("仓库网址", url));
        Toast.makeText(this, "仓库网址已复制", Toast.LENGTH_SHORT).show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTokenDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("ghp_...");
        input.setText(prefs.getString("token", ""));
        new AlertDialog.Builder(this)
                .setTitle("GitHub Token（可选）")
                .setMessage("匿名搜索每小时 10 次、核心 API 每小时 60 次；填 Token 后提升到 30 次/分钟与 5000 次/小时。Token 仅保存在本机。")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.edit().putString("token", input.getText().toString().trim()).apply();
                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
