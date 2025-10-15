# 移除调试相关的类和文件
-assumenosideeffects class kotlin.coroutines.jvm.internal.DebugProbesKt {
    *;
}

# 移除调试探针
-dontwarn kotlinx.coroutines.debug.**
-dontwarn kotlin.coroutines.jvm.internal.DebugProbesKt