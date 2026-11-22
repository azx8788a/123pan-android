# 123pan Next — 原生安卓客户端

基于 [123panNextGen/123pan](https://github.com/123panNextGen/123pan) 的 `fluent-dev` 分支，将接口和业务逻辑改写为 Kotlin。安卓源码位于 `android/`，原桌面工程保留不动。

- 上游基线：`2b226e9fd2ccbe843043c537a04587b7f2d8e5eb`
- Android 改写开始日期：2026-11-22
- Kotlin + Jetpack Compose + OkHttp + Room + WorkManager
- Android 8.0 及以上；当前版本 `0.1.0-alpha01`
- GPL-3.0-or-later，详见仓库根目录 [LICENSE](../LICENSE)

## 第一阶段的功能范围

- 账号密码登录、保存登录状态、切换已保存账号、123 云盘 App 扫码登录。
- 云盘分页浏览、搜索、排序、面包屑、长按多选。
- 创建文件夹、重命名、移动、复制、移入回收站、恢复和永久删除。
- 创建分享、分享列表、复制链接、取消分享。
- 本地文件多选、文件夹递归上传；系统原生文件路径，不使用 SAF。
- 下载文件、云端文件夹打包下载、自定义下载目录。
- 下载续传、服务器不支持 Range 时自动重下、有效 MD5 的完整文件校验。
- 5 MiB 流式分片上传、1–4 路分片并发、暂停后从云端已完成分片继续。
- 上传合并与 `upload_complete/v2` 确认，不仅凭 PUT 成功就标记任务完成。
- Room 持久化任务，前台传输通知，暂停、继续、取消和失败重试。
- 离线下载提交（默认选全部资源）、秒传 JSON / 123FastLink 导入导出。
- 下载后调用其他应用打开文件，跟随系统 / 浅色 / 深色主题。

以上功能已在代码中接入；Mock HTTP 测试不等于真实账号验收。接口兼容性、文件夹打包、扫码、回收站和后台行为需要安装后使用自己的账号逐项验证。

## 文件访问

安装后打开“设置 → 所有文件访问”：

- Android 11+：跳转系统“所有文件访问”设置，由使用者开启。
- Android 8–10：申请存储读写权限。
- 默认下载到共享存储的 `Download/123panNext/`，可以在应用内选择其他目录及存储卷。
- 未完成下载写入同目录的 `.123pan-任务ID.part`；暂停保留断点，取消清除该临时文件。
- 同名下载自动另存为 `文件名 (1).扩展名`；同名云端上传默认保留两者。

Android 自身仍限制部分目录（例如其他应用的 `Android/data`）；授予所有文件访问不等于取得 root。

## 构建 APK

推荐在 GitHub Actions 的 **Android APK** 工作流中选择 `android` 分支，点击 **Run workflow**。

CI 会执行：

```sh
cd android
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

成功后下载 `123pan-next-debug-运行序号` artifact，内含 APK、SHA-256 和同一提交的源码 ZIP。
APK 为开发测试版，CI 通过缓存复用调试签名以支持后续覆盖更新。清空签名缓存后，遇到无法覆盖安装时需卸载旧测试版。

本地构建需要 JDK 17 和 Android SDK 35；将 `android/` 作为 Android Studio 工程打开即可。工作流不需要个人 GitHub 令牌或 123 云盘账号密码。

## 工程结构

```text
app/src/main/java/io/github/azx8788a/pan123/
  api/       上游接口、JSON 模型、HTTP
  data/      账号、设置、Room 任务数据库
  service/   文件、上传下载、秒传与本地存储
  tasks/     WorkManager 任务与通知控制
  ui/        Compose 页面与 ViewModel
```

## 后续迁移清单

- 多分片并行下载及其持久化分片映射。
- 内置图片 / 文本 / PDF / 音视频预览，而非仅调用外部应用。
- 文件夹同步、定时同步、完整限速 / 代理设置。
- 微信扫码确认、登录设备管理、付费分享管理和离线资源选择。
- 将文件夹上传的目录扫描也持久化到任务系统；当前目录扫描在前台发起，已生成的文件上传任务可以继续在后台运行。
- 真实账号与不同 Android 版本的端到端测试。

这是一项持续迁移，不宣称已经与桌面版的全部功能等价。
