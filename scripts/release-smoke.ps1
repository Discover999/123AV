param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$Serial = $env:ANDROID_SERIAL,
    [string]$Package = 'com.av123.video',
    [int]$WaitSeconds = 5,
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'

function Invoke-Adb {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($Serial) {
            $output = & adb -s $Serial @args 2>&1
        } else {
            $output = & adb @args 2>&1
        }
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($code -ne 0) {
        throw "adb $($args -join ' ') 失败：`n$output"
    }
    return $output
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw '找不到 adb，请先把 Android SDK platform-tools 加入 PATH。'
}

if (-not $Serial) {
    $devices = & adb devices
    $online = @($devices | Select-String '^\S+\s+device$')
    if ($online.Count -ne 1) {
        throw '检测到 0 个或多个在线设备；请传 -Serial，或设置 ANDROID_SERIAL。'
    }
    $Serial = ($online[0].Line -split '\s+')[0]
}

$apkPath = (Resolve-Path $Apk).Path

if (-not $SkipInstall) {
    Write-Host "安装 $apkPath ..."
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $install = & adb -s $Serial install -r $apkPath 2>&1
        $installCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    $installText = $install -join "`n"
    if ($installCode -ne 0) {
        if ($installText -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
            throw '签名不一致，无法覆盖安装。请使用干净设备/模拟器，或先导出数据并卸载旧版（会清空应用数据）。'
        }
        throw "安装失败：`n$installText"
    }
    Write-Host $installText.Trim()
}

Invoke-Adb logcat -c | Out-Null
Invoke-Adb shell am force-stop $Package | Out-Null
Invoke-Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
Invoke-Adb shell wm dismiss-keyguard | Out-Null

$start = Invoke-Adb shell am start -W -n "$Package/.MainActivity"
$total = ($start | Select-String 'TotalTime' | Select-Object -First 1).Line
Write-Host "冷启动：$total"

Start-Sleep -Seconds $WaitSeconds
$pidText = (Invoke-Adb shell pidof $Package | Out-String).Trim()
if (-not $pidText) {
    throw "应用进程未存活（$Package）"
}
Write-Host "进程：$pidText"

$remote = '/sdcard/release-smoke.xml'
Invoke-Adb shell uiautomator dump $remote | Out-Null
$local = Join-Path $env:TEMP '123av-release-smoke.xml'
Invoke-Adb pull $remote $local | Out-Null
if (-not (Test-Path $local)) {
    throw "uiautomator dump 拉取失败：$local"
}
$xml = Get-Content -Raw -Encoding UTF8 $local

$missing = @()
foreach ($marker in @('首页', '分栏', '历史', '我的')) {
    if ($xml -notmatch [regex]::Escape($marker)) {
        $missing += $marker
    }
}
if ($missing.Count -gt 0) {
    throw "界面缺少底部导航标记：$($missing -join ', ')。可能启动失败或页面被遮挡。"
}

$crashes = Invoke-Adb logcat -d -b crash
$crashText = $crashes -join "`n"
if ($crashText -match [regex]::Escape($Package)) {
    throw "崩溃缓冲区出现 $Package：`n$crashText"
}

$version = (Invoke-Adb shell dumpsys package $Package |
    Select-String 'versionName=' |
    Select-Object -First 1).Line
Write-Host "版本：$version"
Write-Host 'PASS：安装、冷启动、底部导航与崩溃检查均通过。'
