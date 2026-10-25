#!/bin/sh

#
# CofferTunnel Gradle Wrapper 智能自举脚本
#

set -e

APP_BASE_NAME=${0##*/}
APP_HOME=$(cd -P -- "$(dirname -- "$0")" && pwd -P) || exit
WRAPPER_DIR="$APP_HOME/gradle/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/gradle-wrapper.jar"

# 1. 自动定位 macOS 环境中的 Java 17+（防止 Conda/系统默认 Java 8 干扰）
if [ "$(uname)" = "Darwin" ]; then
    MAC_JDK17=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 21 2>/dev/null || echo "")
    if [ -n "$MAC_JDK17" ] && [ -d "$MAC_JDK17" ]; then
        export JAVA_HOME="$MAC_JDK17"
    fi
fi

# 2. 如果缺少 gradle-wrapper.jar，自动下载
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "[CofferTunnel] 正在初始化 gradle-wrapper.jar..."
    mkdir -p "$WRAPPER_DIR"
    
    DOWNLOAD_SUCCESS=0
    URLS="https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar https://cdn.jsdelivr.net/gh/gradle/gradle@v8.5.0/gradle/wrapper/gradle-wrapper.jar"
    
    for url in $URLS; do
        if curl -fL --connect-timeout 5 -o "$WRAPPER_JAR" "$url" 2>/dev/null; then
            if [ -s "$WRAPPER_JAR" ]; then
                DOWNLOAD_SUCCESS=1
                echo "[CofferTunnel] gradle-wrapper.jar 下载完成。"
                break
            fi
        fi
    done

    if [ "$DOWNLOAD_SUCCESS" -ne 1 ]; then
        rm -f "$WRAPPER_JAR"
        echo "[错误] 自动下载 gradle-wrapper.jar 失败，请检查网络。" >&2
        exit 1
    fi
fi

# 3. 确定 Java 可执行文件路径
JAVACMD="java"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
fi

# 输出当前使用的 Java 版本信息便于确认
CURRENT_JAVA_VER=$("$JAVACMD" -version 2>&1 | head -n 1)
echo "[CofferTunnel] 使用 Java 环境: $CURRENT_JAVA_VER"
if [ -n "$JAVA_HOME" ]; then
    echo "[CofferTunnel] JAVA_HOME: $JAVA_HOME"
fi

exec "$JAVACMD" -cp "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
