<div align="center">

# 123AV

**一款基于 Jetpack Compose 与 Material 3 的 Android 视频客户端**

单 Activity · MVVM · Material You

![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-7F52FF?logo=kotlin&logoColor=white)
![AGP](https://img.shields.io/badge/AGP-8.11.1-3DDC84?logo=androidstudio&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-8.13-02303A?logo=gradle&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.12.01-4285F4?logo=jetpackcompose&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-555555)
![targetSdk](https://img.shields.io/badge/targetSdk-36-555555)
![Version](https://img.shields.io/badge/version-0.1.0-blue)

</div>

> **AI 全程开发说明**
>
> 本项目由 AI 全程辅助开发完成——从架构设计、代码编写、调试测试到文档撰写，均在 AI 编程助手协作下产出。

> **内容来源说明**
>
> 本应用仅聚合来自合法授权视频源的公开内容，不生产、不存储、不上传任何视频。

---

## 目录

- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [环境要求](#环境要求)
- [构建与运行](#构建与运行)
- [项目结构](#项目结构)
- [应用信息](#应用信息)
- [权限说明](#权限说明)
- [免责声明](#免责声明)

---

## 功能特性

### 内容浏览

- 首页精选轮播与信息流
- 栏目 / 类别 / 女演员 / 制作商 / 系列多维度索引
- 关键词搜索与搜索词记录
- 女演员资料页（头像、档案、作品统计）

### 视频播放

- 基于 Media3 / ExoPlayer 播放 m3u8 流媒体
- 多集选集、断点续播、片尾自动连播
- 画中画（PiP）与全屏横竖屏切换
- ExoPlayer 分片缓存（LRU 512MB），二次观看免重复缓冲

### 手势操作

| 手势 | 行为 |
| :--- | :--- |
| 长按画面 | 临时 2 倍速播放，松手恢复 |
| 双击左 / 右半屏 | 快退 / 快进 10 秒 |
| 横向滑动 | 拖动进度条，松手确认 Seek |
| 双击中间区域 | 播放 / 暂停（末集为重播） |

另支持画面比例切换：适应 / 填充 / 裁剪。

### 本地数据

- 观看历史与播放进度
- 视频收藏 + 自定义收藏夹分组（多对多）
- 女演员本地关注，聚合关注演员新片
- Room 数据库持久化，支持旧版数据自动迁移

### 个性化与安全

- Material You 动态取色（Android 12+），低版本回退品牌粉色
- 观看时长统计（周柱图、连续天数、分类占比）
- 生物识别 / 设备凭据应用锁
- 最近任务界面隐私模糊保护
- 平板 / 大屏双栏自适应布局（屏宽 ≥ 840dp）

### 网络与诊断

- 全站共享 OkHttp 连接池，IO 异常自动重试
- 首页 / 列表 / 详情短 TTL 磁盘缓存（SWR：先渲染缓存，后台静默刷新）
- 内置网络诊断页：分段统计 DNS / TCP / TLS / TTFB 耗时，一键生成环境报告

---

## 技术栈

| 类别 | 选型 |
| :--- | :--- |
| **语言 / 构建** | Kotlin 2.1.20 · Gradle 8.13（JDK 17） · AGP 8.11.1 |
| **UI** | Jetpack Compose（BOM 2024.12.01） · Material 3 · Material Icons Extended |
| **导航** | Navigation Compose 2.8（预测式返回手势） |
| **架构** | 单 Activity + MVVM（ViewModel + StateFlow） |
| **本地存储** | Room 2.8.5（KSP） · DataStore Preferences 1.1.1 · kotlinx.serialization 1.7.3 |
| **网络与解析** | OkHttp 4.12 · Jsoup 1.18.3 · 无头 WebView（JS 渲染 / m3u8 抓流） |
| **视频播放** | Media3 / ExoPlayer 1.4.1（HLS + SimpleCache） |
| **图片加载** | Coil 2.7.0（磁盘缓存 150MB） |
| **其他** | core-splashscreen 启动屏 · androidx.biometric 1.1.0 应用锁 |

---

## 环境要求

| 项 | 要求 |
| :--- | :--- |
| JDK | **17**（Gradle 8.13 不兼容更高版本） |
| compileSdk / targetSdk | **36** |
| minSdk | **26**（Android 8.0） |
| IDE | Android Studio（建议较新版本以兼容 AGP 8.11） |

---

## 构建与运行

```bash
# Debug 构建并安装到已连接设备
./gradlew installDebug

# 运行全部单元测试（40 个用例）
./gradlew testDebugUnitTest

# Release 构建（已开启 R8 混淆与资源压缩）
./gradlew assembleRelease
```

> 首次在 Android Studio 打开时等待 Gradle Sync 完成即可；SDK 路径由本地 `local.properties` 配置，该文件不纳入版本管理。

---

## 项目结构

```text
app/src/main/java/com/av123/video/
├── MainActivity.kt            # 单 Activity 入口
├── VideoHubApp.kt             # Application
├── data/
│   ├── model/                 # 领域模型（Video / Episode / ActressProfile…）
│   ├── source/                # 数据源接口 + Jsoup 实现 + 站点 URL
│   ├── net/                   # OkHttp、统一错误类型、网络诊断探针
│   ├── repository/            # 仓库层（首页/详情/历史/收藏/缓存/统计）
│   ├── db/                    # Room 数据库、实体与 DAO
│   ├── playback/              # ExoPlayer 分片缓存
│   └── prefs/                 # DataStore 用户偏好
└── ui/
    ├── navigation/            # 导航图、底部导航栏、双栏适配
    ├── screens/               # 首页/栏目/搜索/详情/历史/收藏/关注/统计/我的/诊断
    ├── components/            # 通用组件与播放器封装
    ├── lock/                  # 生物识别应用锁
    ├── theme/                 # 颜色、主题、字体
    └── util/                  # 窗口尺寸、时间格式等工具
```

测试代码位于 `app/src/test/`，与主源码包结构一一对应。

---

## 应用信息

| 项 | 值 |
| :--- | :--- |
| 应用名称 | **123AV** |
| 包名（applicationId） | `com.av123.video` |
| 当前版本 | 0.1.0（versionCode 1） |

---

## 权限说明

| 权限 | 用途 |
| :--- | :--- |
| `INTERNET` | 网页数据抓取、封面加载与视频流播放 |
| `USE_BIOMETRIC` | 应用锁生物识别验证（设备 PIN / 图案 / 密码由系统弹窗兜底） |

应用仅申请以上两个权限，不读取通讯录、位置、存储等敏感信息。

---

## 免责声明

1. 本应用仅用于**学习与技术研究**，不存储、不上传任何视频内容，所有数据均来自公开网络渠道。
2. 应用内展示内容的版权归原网站及相关权利人所有，本应用不对内容的合法性、准确性、完整性负责。
3. 若本应用聚合的内容侵犯了您的合法权益，请联系内容来源网站处理，或告知我们予以移除。
4. 使用者应遵守所在国家或地区的法律法规，因使用本应用产生的任何纠纷与后果，由使用者自行承担。
5. 本项目由 AI 辅助生成，作者不对软件的稳定性、适用性作任何明示或暗示的担保。

<div align="right">

**123AV** · 仅用于技术交流

</div>
