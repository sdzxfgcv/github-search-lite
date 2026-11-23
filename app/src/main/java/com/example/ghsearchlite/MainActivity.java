package com.example.ghsearchlite;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private EditText keywordEdit;
    private Spinner platformSpin, sortSpin;
    private TextView statusText;
    private ListView listView;
    private ArrayAdapter<Repo> adapter;
    private final List<Repo> results = new ArrayList<>();

    private static final String[] CAT_NAMES = {"🔥 热门", "🆕 新发布", "⭐ 最流行", "📰 每日发现"};
    private static final String[] CAT_KEYS = {"trending", "new-releases", "most-popular", "feed"};
    private static final String[] SORTS = {"最佳匹配", "最多 Star", "最近更新", "最近发布"};
    private static final String[] SORT_KEYS = {"relevance", "stars", "updated", "releases"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Komi Lite");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F5F7);

        TextView title = new TextView(this);
        title.setText("Komi Lite · 开源应用发现");
        title.setTextSize(17);
        title.setTextColor(0xFF222222);
        title.setPadding(16, 14, 16, 6);
        root.addView(title);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(16, 4, 16, 4);
        keywordEdit = new EditText(this);
        keywordEdit.setHint("搜索开源应用，如 all-in");
        keywordEdit.setSingleLine(true);
        keywordEdit.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row1.addView(keywordEdit);
        Button searchBtn = new Button(this);
        searchBtn.setText("搜索");
        searchBtn.setOnClickListener(v -> doSearch());
        row1.addView(searchBtn);
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(16, 2, 16, 2);
        platformSpin = makeSpinner(KomiApi.PLATFORM_LABELS, 1);
        sortSpin = makeSpinner(SORTS, 1);
        row2.addView(platformSpin);
        row2.addView(sortSpin);
        root.addView(row2);

        HorizontalScrollView catScroll = new HorizontalScrollView(this);
        LinearLayout catRow = new LinearLayout(this);
        catRow.setOrientation(LinearLayout.HORIZONTAL);
        catRow.setPadding(16, 6, 16, 2);
        for (int i = 0; i < CAT_NAMES.length; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(CAT_NAMES[i]);
            b.setOnClickListener(v -> loadCategory(idx));
            catRow.addView(b);
        }
        catScroll.addView(catRow);
        root.addView(catScroll);

        statusText = new TextView(this);
        statusText.setText("点上方分类发现应用，或输入关键词搜索。数据来自 Komi 后端索引（已按平台筛过）。");
        statusText.setTextSize(11);
        statusText.setTextColor(0xFF777777);
        statusText.setPadding(16, 6, 16, 6);
        root.addView(statusText);

        listView = new ListView(this);
        listView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        adapter = new ArrayAdapter<Repo>(this, android.R.layout.simple_list_item_2, android.R.id.text1, results) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                Repo r = results.get(position);
                t1.setText(r.fullName);
                t1.setTextColor(0xFF2F6FED);
                String desc = r.description == null ? "" : r.description;
                if (desc.length() > 60) desc = desc.substring(0, 60) + "…";
                String meta = "★" + r.stars + "  fork " + r.forks
                        + (r.language.isEmpty() ? "" : "  " + r.language)
                        + (r.latestRelease.isEmpty() ? "" : "  ▸ " + r.latestRelease);
                t2.setText(desc + "\n" + meta);
                t2.setTextSize(11);
                return v;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(this, DetailActivity.class);
            intent.putExtra("fullName", results.get(position).fullName);
            startActivity(intent);
        });
        root.addView(listView);

        setContentView(root);

        loadCategory(0);
    }

    private Spinner makeSpinner(String[] items, float weight) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        return s;
    }

    private String platform() {
        return KomiApi.PLATFORMS[platformSpin.getSelectedItemPosition()];
    }

    private void doSearch() {
        String q = keywordEdit.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(this, "请输入关键词", Toast.LENGTH_SHORT).show();
            return;
        }
        final String sort = SORT_KEYS[sortSpin.getSelectedItemPosition()];
        final String plat = platform();
        statusText.setText("搜索中…");
        fetch(() -> KomiApi.search(q, plat, sort, 30), "搜索完成");
    }

    private void loadCategory(int idx) {
        final String key = CAT_KEYS[idx];
        final String plat = platform();
        statusText.setText("加载中…");
        fetch(() -> "feed".equals(key)
                ? KomiApi.feed(plat, 1, 30)
                : KomiApi.category(key, "all".equals(plat) ? "android" : plat),
                CAT_NAMES[idx] + " 加载完成");
    }

    private void fetch(ApiCall call, final String doneMsg) {
        new Thread(() -> {
            try {
                final List<Repo> data = call.run();
                runOnUiThread(() -> {
                    results.clear();
                    results.addAll(data);
                    adapter.notifyDataSetChanged();
                    statusText.setText(doneMsg + " · " + data.size() + " 个应用");
                });
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("错误：" + e.getMessage()));
            }
        }).start();
    }

    interface ApiCall {
        List<Repo> run() throws Exception;
    }
}
