package com.example.ghsearchlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class GitHubApi {
    static final String[] PLATFORMS = {"全部", "Android", "Windows", "macOS", "Linux", "iOS"};

    private static final Map<String, String[]> PLATFORM_SUFFIXES = new HashMap<>();
    static {
        PLATFORM_SUFFIXES.put("Android", new String[]{".apk"});
        PLATFORM_SUFFIXES.put("Windows", new String[]{".exe", ".msi"});
        PLATFORM_SUFFIXES.put("macOS", new String[]{".dmg", ".pkg"});
        PLATFORM_SUFFIXES.put("Linux", new String[]{".appimage", ".deb", ".rpm", ".pkg.tar.zst"});
        PLATFORM_SUFFIXES.put("iOS", new String[]{".ipa"});
    }

    private static final Map<String, String[]> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(8);

    private static String request(String urlString, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "ghsearch-lite");
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.trim().isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token.trim());
        }
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
        if (code != 200) {
            String msg = sb.substring(0, Math.min(160, sb.length()));
            if (code == 403) msg = "API 次数超限，建议到「设置 Token」填入令牌";
            else if (code == 401) msg = "Token 无效，请到「设置 Token」检查";
            throw new Exception("HTTP " + code + " " + msg);
        }
        return sb.toString();
    }

    static List<Repo> search(String keyword, String language, int minStars, int days,
                             String sort, String token, int page) throws Exception {
        StringBuilder q = new StringBuilder();
        if (keyword != null && !keyword.trim().isEmpty()) q.append(keyword.trim());
        if (!language.isEmpty()) {
            if (q.length() > 0) q.append(' ');
            q.append("language:").append(language);
        }
        if (minStars > 0) {
            if (q.length() > 0) q.append(' ');
            q.append("stars:>=").append(minStars);
        }
        if (days > 0) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -days);
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
            if (q.length() > 0) q.append(' ');
            q.append("pushed:>=").append(date);
        }
        if (q.length() == 0) throw new Exception("请输入关键词或选择筛选条件");

        // GitHub 的 sort 只接受 stars/forks/updated，"最佳匹配"必须省略该参数
        String sortParam = "best".equals(sort) ? "" : "&sort=" + sort + "&order=desc";
        String url = "https://api.github.com/search/repositories?q="
                + URLEncoder.encode(q.toString(), "UTF-8")
                + sortParam + "&per_page=20&page=" + page;
        String body = request(url, token);
        JSONObject root = new JSONObject(body);
        JSONArray items = root.getJSONArray("items");
        List<Repo> list = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.getJSONObject(i);
            JSONObject owner = o.optJSONObject("owner");
            String ownerName = owner == null ? "" : owner.optString("login", "");
            list.add(new Repo(
                    o.optString("full_name", ""),
                    o.optString("description", ""),
                    o.optString("html_url", ""),
                    o.optString("language", ""),
                    o.optInt("stargazers_count", 0),
                    o.optInt("forks_count", 0),
                    o.optString("pushed_at", "")
            ));
        }
        return list;
    }

    private static String[] detectPlatforms(String owner, String repoName, String token) {
        String key = owner + "/" + repoName;
        String[] cached = CACHE.get(key);
        if (cached != null) return cached;

        try {
            String body = request(
                    "https://api.github.com/repos/" + owner + "/" + repoName + "/releases?per_page=5",
                    token);
            JSONArray releases = new JSONArray(body);
            JSONObject stable = null;
            for (int i = 0; i < releases.length(); i++) {
                JSONObject r = releases.getJSONObject(i);
                if (!r.optBoolean("draft", false) && !r.optBoolean("prerelease", false)) {
                    stable = r;
                    break;
                }
            }
            Set<String> found = new LinkedHashSet<>();
            if (stable != null) {
                JSONArray assets = stable.optJSONArray("assets");
                if (assets != null) {
                    for (int j = 0; j < assets.length(); j++) {
                        String name = assets.getJSONObject(j).optString("name", "")
                                .toLowerCase(Locale.US);
                        for (Map.Entry<String, String[]> e : PLATFORM_SUFFIXES.entrySet()) {
                            for (String suffix : e.getValue()) {
                                if (name.endsWith(suffix)) found.add(e.getKey());
                            }
                        }
                    }
                }
            }
            String[] result = found.toArray(new String[0]);
            CACHE.put(key, result);
            return result;
        } catch (Exception e) {
            return new String[0];
        }
    }

    static List<Repo> filterByPlatform(List<Repo> repos, String platform, String token) {
        if ("全部".equals(platform)) return repos;

        List<Future<String[]>> futures = new ArrayList<>();
        for (Repo r : repos) {
            String[] parts = r.htmlUrl.split("/");
            if (parts.length < 2) {
                futures.add(null);
                continue;
            }
            final String owner = parts[parts.length - 2];
            final String name = parts[parts.length - 1];
            futures.add(POOL.submit(() -> detectPlatforms(owner, name, token)));
        }

        List<Repo> out = new ArrayList<>();
        for (int i = 0; i < repos.size(); i++) {
            try {
                Future<String[]> f = futures.get(i);
                String[] platforms = f == null ? new String[0] : f.get(4, TimeUnit.SECONDS);
                repos.get(i).platforms = platforms.length == 0
                        ? "-" : Arrays.toString(platforms).replace("[", "").replace("]", "");
                if (Arrays.asList(platforms).contains(platform)) out.add(repos.get(i));
            } catch (Exception ignored) {
            }
        }
        return out;
    }
}
