# 本项目使用 Compose + 反射很少，保留规则极简。
# 协议层是纯 Kotlin 数据类，无需保留。

# 保留行号，便于线上崩溃定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# DataStore / Proto 相关（当前只用 Preferences，防止未来接入时被裁剪）
-keepclassmembers class * extends androidx.datastore.core.Serializer { *; }

# 避免 Compose 运行时被裁剪
-dontwarn androidx.compose.**
