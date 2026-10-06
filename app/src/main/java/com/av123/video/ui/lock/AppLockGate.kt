package com.av123.video.ui.lock

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
 * 应用锁门禁：仅按冷启动快照 [enabledAtLaunch] 决定本进程是否加锁
 * （运行时才开启不锁当前进程、运行时关闭本进程也不改变已解锁状态，符合"仅冷启动验证"）。
 *
 * 未启用时直接渲染 [content]；启用时 [content] 始终保留在组合中，上方盖品牌锁屏，
 * 验证成功后移除锁屏。取消/报错时把任务切到后台，回前台（ON_RESUME）自动重弹。
 */
@Composable
fun AppLockGate(
    enabledAtLaunch: Boolean,
    content: @Composable () -> Unit
) {
    content()

    if (!enabledAtLaunch) return

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? FragmentActivity }
    // 解锁态只在本进程内存中；true 后锁屏移除，进程被杀重启重新验证
    var unlocked by remember { androidx.compose.runtime.mutableStateOf(false) }
    // 认证触发计数：进入即 1（首次 ON_RESUME 可能已过），之后每次 ON_RESUME +1
    var promptTick by remember { mutableIntStateOf(1) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !unlocked) promptTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!unlocked) {
        val title = stringResource(R.string.app_lock_prompt_title)
        val subtitle = stringResource(R.string.app_lock_unlock)
        // 计数变化（进入与每次回前台）自动弹系统验证
        androidx.compose.runtime.LaunchedEffect(promptTick, activity) {
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
