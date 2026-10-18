# P0 性能基线

测量日期：2026-10-18  
设备：OnePlus PJD110 / Android 16 / Wi-Fi adb `192.168.31.156:44529`  
包名：`com.av123.video`

| 指标 | 当前值 | 测量方式 |
|---|---|---|
| 冷启动 TotalTime | 1010 / 1089 / 1252 / 1393 ms | `am start -W`，4 次单次采样，非统计均值 |
| 首帧 | 803 ms，Skipped 102 frames | 首次冷启动日志（含 WebView 初始化） |
| 进程 RSS | 403–463 MB | `dumpsys activity exit-info` 历史记录 |
| Media3 分片缓存 | 上限 512 MB | `MediaCache.MAX_CACHE_BYTES` |
| Coil 图片缓存 | 上限 150 MB | `VideoHubApp.newImageLoader()` |
| `page_cache` | 无上限 | `PageCacheStore` 当前无淘汰策略 |
| 单元测试 | 6 个测试文件 | `testDebugUnitTest` |
| 迁移测试 | 1 个 androidTest | `connectedDebugAndroidTest` |

> 这组数字只用于后续阶段对比，不是性能承诺。测量时设备处于同一 Wi-Fi、同一代理环境。
