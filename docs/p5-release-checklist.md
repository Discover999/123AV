# P5 发布冒烟与回滚清单

适用：`v*` tag 触发的 Release APK。发布前逐项通过；任一项失败则不发版。

## 1. 自动检查

| 检查 | 命令 / 位置 | 通过标准 |
|---|---|---|
| lint + 单测 + release 构建 | Release workflow / 本地 | `lintDebug testDebugUnitTest assembleRelease` 成功 |
| 版本注入 | tag `vX.Y.Z` | `versionName=X.Y.Z`，`versionCode=GITHUB_RUN_NUMBER` |
| 签名 | workflow Secrets | `apksigner verify` 通过，keystore 不变 |
| APK 产物 | `app/build/outputs/apk/release/app-release.apk` | 文件存在且可安装 |

本地复核签名：

```bash
APKSIGNER=$(find "$ANDROID_HOME/build-tools" -name apksigner | sort -V | tail -1)
"$APKSIGNER" verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## 2. 真机冒烟

> Release 与本地 debug 签名不同，直接覆盖会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。
> 不要在保留数据的设备上强行卸载；优先使用干净设备/模拟器，或先导出 JSON 备份再卸载。

```bash
adb install -r app-release.apk
powershell -File scripts/release-smoke.ps1 -Apk app-release.apk -Serial <serial>
```

脚本会自动检查：安装、冷启动、应用进程、底部导航（首页/分栏/历史/我的）与崩溃缓冲区。

手工检查：

- [ ] 首页 feed、分类/分栏、搜索至少一条链路可用
- [ ] 详情页打开，选集/相关推荐正常；媒体可用时能起播
- [ ] 网络诊断：站点状态、站点配置、运行指标卡都有值
- [ ] 应用锁：开启后切后台再回来会重新验证；最近任务预览不泄露画面（FLAG_SECURE）
- [ ] 数据与备份：导出 JSON、导入恢复；清理缓存有确认弹窗
- [ ] `logcat -b crash` 无本应用 FATAL；无 ANR

通过标准：无崩溃/ANR、无破坏性迁移、版本号正确、关键链路可用。站点/CDN 不可用记录为环境限制，不当作代码回归。

## 3. 回滚流程

Android 不允许安装更低 `versionCode`。回滚 = **用更高 versionCode 发布旧代码**，不是降级安装。

1. 标记问题 Release 为 pre-release，或在 Release notes 说明影响范围；已安装的用户不会自动降级。
2. 在 `main` 上 `git revert <bad-sha>`，或从最后一个好 tag 新建分支 cherry-pick 修复。
3. 新 tag（例如 `v0.1.2`）触发 Release；`GITHUB_RUN_NUMBER` 保证 `versionCode` 递增。
4. 构建后重跑本清单第 1、2 节，确认版本号与签名。
5. 发布新 Release，notes 写明回滚原因与受影响版本。
6. Room schema 只能前进：旧代码读不了新库时，必须在回滚版本补 migration，或选择前向修复；不要重新引入 `fallbackToDestructiveMigration`。
7. 站点选择器/域名问题优先改 `site-config.json`（下次冷启动生效），必要时 `remoteEnabled=false` 做 kill switch。

## 4. 命令速查

```bash
./gradlew lintDebug testDebugUnitTest assembleRelease \
  -PversionCode=<run_number> -PversionName=<x.y.z>

APKSIGNER=$(find "$ANDROID_HOME/build-tools" -name apksigner | sort -V | tail -1)
"$APKSIGNER" verify --print-certs app/build/outputs/apk/release/app-release.apk

adb install -r app/build/outputs/apk/release/app-release.apk
powershell -File scripts/release-smoke.ps1 -Apk <apk> -Serial <serial>
```
