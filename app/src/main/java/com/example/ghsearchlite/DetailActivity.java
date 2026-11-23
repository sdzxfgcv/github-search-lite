package com.example.ghsearchlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class DetailActivity extends Activity {
    private String fullName;
    private Repo repo;
    private TextView infoText;
    private ListView assetList;
    private ArrayAdapter<KomiApi.Asset> adapter;
    private final List<KomiApi.Asset> assets = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fullName = getIntent().getStringExtra("fullName");
        setTitle(fullName);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F5F7);

        infoText = new TextView(this);
        infoText.setTextSize(13);
        infoText.setTextColor(0xFF333333);
        infoText.setPadding(16, 14, 16, 10);
        root.addView(infoText);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(16, 4, 16, 4);
        Button copyBtn = new Button(this);
        copyBtn.setText("复制仓库网址");
        copyBtn.setOnClickListener(v -> copyUrl("https://github.com/" + fullName));
        Button openBtn = new Button(this);
        openBtn.setText("打开 GitHub");
        openBtn.setOnClickListener(v -> openUrl("https://github.com/" + fullName));
        btnRow.addView(copyBtn);
        btnRow.addView(openBtn);
        root.addView(btnRow);

        TextView assetTitle = new TextView(this);
        assetTitle.setText("最新稳定版 Release 产物：");
        assetTitle.setTextSize(13);
        assetTitle.setTextColor(0xFF555555);
        assetTitle.setPadding(16, 8, 16, 4);
        root.addView(assetTitle);

        assetList = new ListView(this);
        assetList.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        adapter = new ArrayAdapter<KomiApi.Asset>(this, android.R.layout.simple_list_item_2, android.R.id.text1, assets) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                KomiApi.Asset a = assets.get(position);
                t1.setText(a.name);
                t1.setTextSize(13);
                t2.setText(String.format("%.1f MB  ·  %d 次下载", a.size / 1048576.0, a.downloads));
                t2.setTextSize(11);
                return v;
            }
        };
        assetList.setAdapter(adapter);
        assetList.setOnItemClickListener((parent, view, position, id) -> {
            final KomiApi.Asset a = assets.get(position);
            new AlertDialog.Builder(this)
                    .setTitle(a.name)
                    .setMessage("大小：" + String.format("%.1f MB", a.size / 1048576.0)
                            + "\n下载次数：" + a.downloads + "\n\n" + a.url)
                    .setPositiveButton("复制下载链接", (d, w) -> copyUrl(a.url))
                    .setNeutralButton("用浏览器下载", (d, w) -> openUrl(a.url))
                    .setNegativeButton("取消", null)
                    .show();
        });
        root.addView(assetList);

        setContentView(root);
        load();
    }

    private void load() {
        new Thread(() -> {
            try {
                final Repo r = KomiApi.repoDetail(fullName);
                final List<KomiApi.Asset> a = KomiApi.latestReleaseAssets(fullName);
                runOnUiThread(() -> {
                    repo = r;
                    infoText.setText(r.fullName + "\n"
                            + (r.description == null ? "" : r.description) + "\n\n"
                            + "★ " + r.stars + "  fork " + r.forks
                            + (r.language.isEmpty() ? "" : "  语言 " + r.language)
                            + "\n最新版本 " + (r.latestRelease.isEmpty() ? "—" : r.latestRelease)
                            + (r.latestReleaseDate.isEmpty() ? "" : "  (" + r.latestReleaseDate.substring(0, 10) + ")"));
                    assets.clear();
                    assets.addAll(a);
                    adapter.notifyDataSetChanged();
                    if (a.isEmpty()) {
                        infoText.append("\n\n该仓库最新稳定版没有可下载产物");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> infoText.setText("加载失败：" + e.getMessage()));
            }
        }).start();
    }

    private void copyUrl(String url) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("链接", url));
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }
}
