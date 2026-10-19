# P4 性能基线

测量日期：2026-10-19
设备：OnePlus PJD110 / Android 16 / Wi-Fi adb `192.168.31.156:43807`
包名：`com.av123.video`
构建：`benchmark` build type（release 级 R8 + debug 签名）
基线代码：`d8fe594`（P4.4 的 benchmark 模块改动未提交）

## 冷启动

`am start -W`，每次先 `am force-stop`，共 5 次：

| 次数 | TotalTime | Displayed |
|---|---:|---:|
| 1 | 228 ms | +228 ms |
| 2 | 216 ms | +216 ms |
| 3 | 226 ms | +226 ms |
| 4 | 218 ms | +218 ms |
| 5 | 227 ms | +227 ms |

| 统计 | 值 |
|---|---:|
| 中位数 | 226 ms |
| 均值 | 223 ms |
| 最小 / 最大 | 216 / 228 ms |

## 内存与体积

冷启动后空闲 3 秒，`dumpsys meminfo com.av123.video`：

| 指标 | 值 |
|---|---:|
| TOTAL PSS | 90,756 kB（约 88.6 MiB） |
| TOTAL RSS | 291,672 kB（约 284.8 MiB） |
| TOTAL SWAP PSS | 62 kB |
| benchmark APK | 4,963,999 B（约 4.96 MB） |

## 与 P0 基线对比

| 指标 | P0（2026-10-18） | P4（2026-10-19） | 说明 |
|---|---:|---:|---|
| 冷启动 | 1010–1393 ms | 216–228 ms | P0 未标注 build type；本次为 benchmark 变体，且 Macrobenchmark 安装后可能带有编译产物，不能直接视为同一口径 |
| 内存 | RSS 403–463 MB | PSS 88.6 MiB / RSS 284.8 MiB | P0 取 `exit-info` 历史 RSS，本次取当前 `dumpsys meminfo`，指标口径不同 |
| APK | 未记录 | 4.96 MB | benchmark 变体 |

> 这组数字用于后续 P4/P5 内部对比，不是跨构建类型的性能承诺。

## Macrobenchmark 说明

已接入 `:benchmark` 模块：

```bash
set "ANDROID_SERIAL=192.168.31.156:43807" && .\gradlew.bat :benchmark:connectedBenchmarkAndroidTest
```

- 首次使用默认 `CompilationMode.DEFAULT`、5 轮冷启动运行时，实测耗时约 **532 秒**，Gradle 命令因超时被手动终止；测试 XML 虽记录通过，但未产出可用的 `StartupTimingMetric` 数据。
- 为避免再次长时间占用设备，`StartupBenchmark` 已改为 **3 轮 + `CompilationMode.None()`**；需要严格 release 编译态对比时，可改回 `CompilationMode.DEFAULT`。
- 本次基线因此采用短采样：5 次 `am start -W` + 一次 `dumpsys meminfo`。

## 未覆盖项

- 首页滚动/详情页打开的帧耗时：首页依赖站点网络，CDN 当前不稳定，暂不纳入基线。
- 播放起播耗时：媒体域名 `806l.trueboldmove.site` 当前 DNS/SSL 不可达。
