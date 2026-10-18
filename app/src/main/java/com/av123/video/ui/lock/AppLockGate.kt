package com.av123.video.ui.lock

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.av123.video.R
import com.av123.video.ui.components.BrandLogo
import com.av123.video.ui.util.findActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock

/** 应用锁允许的认证方式：强生物识别，失败可回退设备凭据（PIN/图案/密码） */
private const val LOCK_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * 设备是否具备应用锁认证条件（已录入强生物识别或设置了屏幕锁）。
 * 开启应用锁开关前必须先过此判定，避免开启后无法解锁。
 */
fun canAuthenticateAppLock(context: android.content.Context): Boolean =
    BiometricManager.from(context).canAuthenticate(LOCK_AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

/**
 * 拉起系统生物识别 / 设备凭据验证。回调均在主线程：
 * - 成功：[onSuccess]；
 * - 错误（取消、负向按钮、锁定等终态）：[onError]；单次指纹失败由系统继续提示，不上抛。
 *
 * 注意：同时声明 DEVICE_CREDENTIAL 时禁止 setNegativeButtonText（系统会抛异常），
 * 取消/改用密码由系统弹窗自身承载。
 */
internal fun authenticateAppLock(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (Int, String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errorCode, errString.toString())
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
        .build()
    prompt.authenticate(info)
}

/**
 * 应用锁门禁：
 * - 冷启动按 [enabledAtLaunch] 决定是否启用；启用后本进程解锁；
 * - 离开超过 [relockAfterMs] 后回前台重新验证（0 表示立即）；
 * - 未解锁时不组合 [content]，避免内容在锁屏下被截图或无障碍读取；
 * - 取消/报错切到后台，回前台自动重弹。
 */
@Composable
fun AppLockGate(
    enabledAtLaunch: Boolean,
    relockAfterMs: Long,
    content: @Composable () -> Unit
) {
    if (!enabledAtLaunch) {
        content()
        return
    }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? FragmentActivity }
    // 解锁态只在本进程内存中；true 后渲染 content，进程被杀重启重新验证
    var unlocked by remember { androidx.compose.runtime.mutableStateOf(false) }
    // 认证触发计数：进入即 1（首次 ON_RESUME 可能已过），之后每次需要验证时 +1
    var promptTick by remember { mutableIntStateOf(1) }
    // 进入后台的时间点（elapsedRealtime），0 表示当前不在“离开待判定”状态
    var backgroundedAt by remember { androidx.compose.runtime.mutableStateOf(0L) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, relockAfterMs) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // 画中画仍属于前台可见播放，不算离开应用
                    if (unlocked && activity?.isInPictureInPictureMode != true) {
                        backgroundedAt = SystemClock.elapsedRealtime()
                    }
                }

                Lifecycle.Event.ON_RESUME -> {
                    if (!unlocked) {
                        promptTick++
                    } else if (backgroundedAt > 0L) {
                        val awayMs = SystemClock.elapsedRealtime() - backgroundedAt
                        if (relockAfterMs <= 0L || awayMs >= relockAfterMs) {
                            unlocked = false
                            promptTick++
                        }
                        backgroundedAt = 0L
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val title = stringResource(R.string.app_lock_prompt_title)
    val subtitle = stringResource(R.string.app_lock_unlock)
    // 计数变化（进入、超时回前台）自动弹系统验证；已解锁时不弹
    androidx.compose.runtime.LaunchedEffect(promptTick, activity, unlocked) {
        if (unlocked) return@LaunchedEffect
        val act = activity ?: return@LaunchedEffect
        authenticateAppLock(
            activity = act,
            title = title,
            subtitle = subtitle,
            onSuccess = { unlocked = true },
            onError = { _, _ ->
                // 取消/报错：切到后台，避免停留在未验证界面；回前台自动重弹
                act.moveTaskToBack(true)
            }
        )
    }

    if (unlocked) {
        content()
    } else {
        AppLockOverlay(
            onUnlockClick = {
                val act = activity ?: return@AppLockOverlay
                authenticateAppLock(
                    activity = act,
                    title = title,
                    subtitle = subtitle,
                    onSuccess = { unlocked = true },
                    onError = { _, _ -> act.moveTaskToBack(true) }
                )
            }
        )
    }
}

/** 品牌锁屏：渐变图标 + 标题 + 手动解锁按钮（系统弹窗未出现/被滑掉时兜底触发） */
@Composable
private fun AppLockOverlay(onUnlockClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandLogo(style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(40.dp))
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_lock_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_lock_unlock),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onUnlockClick) {
                Text(stringResource(R.string.app_lock_unlock_button))
            }
        }
    }
}
