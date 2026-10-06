# 默认混淆规则由 Android Gradle Plugin 提供，此处保留模块级自定义配置。
# Jsoup / Media3 / Coil 自带 consumer rules，一般无需额外配置。

# 保留泛型签名与内部类信息（kotlinx.coroutines / 调试可读性）
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# 行号保留：release 崩溃日志可定位（类名/方法名仍会混淆）
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# WebView 安全兜底：本项目未通过 @JavascriptInterface 暴露原生对象，
# 若未来添加 JS 接口，接口方法必须保留，否则 JS 无法回调
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
