#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

# ========================= 基本配置 =========================
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$PROJECT_DIR/src"
LIB_DIR="$PROJECT_DIR/lib"

APP_NAME="Sudo"
VERSION="1.0.0"
MODULE_NAME="com.sudo.app"
MAIN_CLASS="com.sudo.Main"

ICON_PATH="$PROJECT_DIR/Sudoku.icns"

# JavaFX 模块（按需要增减）
JAVAFX_MODULES="javafx.controls,javafx.fxml,javafx.graphics"

# ========================= JAVA_HOME 自动检测 =========================
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  echo "使用环境变量 JAVA_HOME: $JAVA_HOME"
else
  JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  echo "自动检测到 JAVA_HOME: $JAVA_HOME"
fi

JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"
JLINK="$JAVA_HOME/bin/jlink"
JPACKAGE="$JAVA_HOME/bin/jpackage"

# ========================= JavaFX SDK / jmods 检查 =========================
JAVAFX_SDK_DIR="$LIB_DIR/javafx-sdk-17.0.17"
JAVAFX_JMODS_DIR="$LIB_DIR/javafx-jmods-17.0.17"

echo "使用 JavaFX SDK:    $JAVAFX_SDK_DIR"
echo "使用 JavaFX jmods:  $JAVAFX_JMODS_DIR"

# ========================= 构建目录 =========================
BUILD_DIR="$PROJECT_DIR/build"
MODS_DIR="$BUILD_DIR/mods"
MLIB_DIR="$BUILD_DIR/mlib"
IMAGE_DIR="$BUILD_DIR/image"
TMP_SRC="$BUILD_DIR/tmp_src"
PKG_DIR="$BUILD_DIR/installer"

# 彻底清理旧构建
rm -rf "$BUILD_DIR"
mkdir -p "$MODS_DIR" "$MLIB_DIR" "$IMAGE_DIR" "$TMP_SRC" "$PKG_DIR"

# ========================= 拷贝源码 =========================
echo "复制源码..."
rsync -a "$SRC_DIR/" "$TMP_SRC/$MODULE_NAME/"

# ========================= 生成 module-info.java =========================
cat > "$TMP_SRC/$MODULE_NAME/module-info.java" <<EOF
module $MODULE_NAME {
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;

    opens com.sudo to javafx.graphics, javafx.fxml;
    exports com.sudo;
}
EOF

# ========================= 编译 =========================
echo "编译 Java 源码..."

mkdir -p "$MODS_DIR/$MODULE_NAME"

"$JAVAC" \
  --module-source-path "$TMP_SRC" \
  --module-path "$JAVAFX_SDK_DIR/lib" \
  -d "$MODS_DIR" \
  $(find "$TMP_SRC" -name "*.java")

# ========================= 打包 modular JAR =========================
echo "创建模块化 JAR..."

pushd "$MODS_DIR" > /dev/null
"$JAR" --create --file "$MLIB_DIR/$APP_NAME.jar" -C "$MODS_DIR/$MODULE_NAME" .
popd > /dev/null

# ========================= JLINK 最小运行时 =========================
echo "生成 runtime image..."

# 删除可能残留的 image 目录
rm -rf "$IMAGE_DIR"

"$JLINK" \
  --module-path "$MLIB_DIR:$JAVA_HOME/jmods:$JAVAFX_JMODS_DIR" \
  --add-modules "$MODULE_NAME,$JAVAFX_MODULES" \
  --output "$IMAGE_DIR" \
  --launcher "$APP_NAME=$MODULE_NAME/$MAIN_CLASS" \
  --strip-debug \
  --compress=2 \
  --no-header-files \
  --no-man-pages

echo "runtime image 生成完毕"

# ========================= 打包 DMG =========================
echo "生成 DMG..."

JPACKAGE_ARGS=(
  --type dmg
  --name "$APP_NAME"
  --app-version "$VERSION"
  --input "$MLIB_DIR"
  --module "$MODULE_NAME/$MAIN_CLASS"
  --runtime-image "$IMAGE_DIR"
  --dest "$PKG_DIR"
)

# 图标存在则添加
if [[ -f "$ICON_PATH" ]]; then
  JPACKAGE_ARGS+=( --icon "$ICON_PATH" )
fi

"$JPACKAGE" "${JPACKAGE_ARGS[@]}"

echo ""
echo "🎉 打包完成！DMG 输出目录："
echo "   $PKG_DIR"
