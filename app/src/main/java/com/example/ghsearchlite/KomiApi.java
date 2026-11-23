package com.example.ghsearchlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** 封装 Komi Store 后端 API（https://api.github-store.org/v1/），零第三方依赖。 */
class KomiApi {
    private static final String BASE = "https://api.github-store.org/v1/";
    static final String[] PLATFORMS = {"all", "android", "windows", "macos", "linux"};
    static final String[] PLATFORM_LABELS = {"全部平台", "Android", "Windows", "macOS", "Linux"};

    private static String get(String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + path).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "komi-lite-android");
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
        }
        conn.disconnect();
        if (code != 200) throw new Exception("HTTP " + code + " " + sb.substring(0, Math.min(120, sb.length())));
        return sb.toString();
    }

    static List<Repo> parseRepos(String json) throws Exception {
        List<Repo> list = new ArrayList<>();
        JSONArray arr;
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            arr = new JSONArray(trimmed);
        } else {
            JSONObject obj = new JSONObject(trimmed);
            arr = obj.optJSONArray("items");
        }
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            JSONObject owner = o.optJSONObject("owner");
            String ownerName = owner == null ? "" : owner.optString("login", "");
            Repo r = new Repo(
                    o.optString("fullName", ""),
                    o.optString("description", ""),
                    o.optString("htmlUrl", ""),
                    o.optString("language", ""),
                    o.optInt("stargazersCount", 0),
                    o.optInt("forksCount", 0),
                    o.optString("pushedAt", "")
            );
            r.latestRelease = o.optString("latestReleaseTag", "");
            r.latestReleaseDate = o.optString("latestReleaseDate", "");
            JSONArray topics = o.optJSONArray("topics");
            if (topics != null && topics.length() > 0) {
                StringBuilder tb = new StringBuilder();
                for (int j = 0; j < Math.min(4, topics.length()); j++) {
                    if (j > 0) tb.append(' ');
                    tb.append('#').append(topics.optString(j));
                }
                r.topics = tb.toString();
                r.owner = ownerName;
            }
            list.add(r);
        }
        return list;
    }

    static List<Repo> search(String query, String platform, String sort, int limit) throws Exception {
        StringBuilder p = new StringBuilder("q=" + URLEncoder.encode(query, "UTF-8"));
        if (!"all".equals(platform)) p.append("&platform=").append(platform);
        if (sort != null) p.append("&sort=").append(sort);
        p.append("&limit=").append(limit);
        return parseRepos(get("search?" + p));
    }

    static List<Repo> feed(String platform, int page, int limit) throws Exception {
        StringBuilder p = new StringBuilder("page=" + page + "&limit=" + limit);
        if (!"all".equals(platform)) p.append("&platform=").append(platform);
        return parseRepos(get("feed?" + p));
    }

    static List<Repo> category(String cat, String platform) throws Exception {
        return parseRepos(get("categories/" + cat + "/" + platform));
    }

    static List<Repo> topic(String bucket, String platform) throws Exception {
        return parseRepos(get("topics/" + bucket + "/" + platform));
    }

    /** 仓库详情（当前只取展示用字段，够用即可） */
    static Repo repoDetail(String fullName) throws Exception {
        String json = get("repo/" + fullName);
        JSONObject o = new JSONObject(json);
        JSONObject owner = o.optJSONObject("owner");
        String ownerName = owner == null ? "" : owner.optString("login", "");
        Repo r = new Repo(
                o.optString("fullName", fullName),
                o.optString("description", ""),
                o.optString("htmlUrl", "https://github.com/" + fullName),
                o.optString("language", ""),
                o.optInt("stargazersCount", 0),
                o.optInt("forksCount", 0),
                o.optString("pushedAt", "")
        );
        r.latestRelease = o.optString("latestReleaseTag", "");
        r.latestReleaseDate = o.optString("latestReleaseDate", "");
        r.owner = ownerName;
        return r;
    }

    /** release 资产条目 */
    static class Asset {
        String name;
        long size;
        long downloads;
        String url;
    }

    /** 取最新稳定版 release 的资产列表 */
    static List<Asset> latestReleaseAssets(String fullName) throws Exception {
        String json = get("releases/" + fullName + "?page=1&per_page=5");
        JSONArray releases = new JSONArray(json);
        JSONObject stable = null;
        for (int i = 0; i < releases.length(); i++) {
            JSONObject r = releases.getJSONObject(i);
            if (!r.optBoolean("draft", false) && !r.optBoolean("prerelease", false)) {
                stable = r;
                break;
            }
        }
        List<Asset> out = new ArrayList<>();
        if (stable == null) return out;
        JSONArray assets = stable.optJSONArray("assets");
        if (assets == null) return out;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            Asset item = new Asset();
            item.name = a.optString("name", "");
            item.size = a.optLong("size", 0);
            item.downloads = a.optLong("download_count", 0);
            item.url = a.optString("browser_download_url", "");
            out.add(item);
        }
        return out;
    }
}
