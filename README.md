# GitHub 搜索 Lite

一个**零第三方依赖**的极简 GitHub 搜索 Android App。纯 Java + Android Framework API，不用 Compose、AndroidX、OkHttp、Kotlin——体积只有同功能 Compose 版的几十分之一。

## 功能

- 关键词搜索（仓库名 / 描述），可叠加语言、Star 数、更新时间、排序筛选
- **按平台过滤可安装应用**：全部 / Android / Windows / macOS / Linux / iOS
  - 判定逻辑与 Komi Store 一致：拉取仓库最近 5 个 release，取第一个稳定版，按资产文件名后缀判断
  - Android→`.apk`；Windows→`.exe`/`.msi`；macOS→`.dmg`/`.pkg`；Linux→`.appimage`/`.deb`/`.rpm`/`.pkg.tar.zst`；iOS→`.ipa`
  - 8 线程并发检测、内存缓存、单仓库 4 秒超时
- 每个结果显示检测到的支持平台
- 点击结果：一键复制仓库网址 / 用浏览器打开
- 可选 Token（本机保存）：匿名搜索 10 次/小时、核心 API 60 次/小时；填 Token 后 30 次/分钟、5000 次/小时

## 为什么这么小

网络用系统自带 `HttpURLConnection`，JSON 用系统自带 `org.json`，UI 用系统自带 `EditText`/`Spinner`/`ListView`，没有任何第三方库被打进 APK。搜索与过滤逻辑本身只有几 KB。

## 构建

推送到 `main` 分支由 GitHub Actions 自动构建并发布 Release：

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`
