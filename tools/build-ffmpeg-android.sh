#!/usr/bin/env bash
# Сборка статических ffmpeg/ffprobe под Android (bionic) из исходников jellyfin-ffmpeg.
#
#   tools/build-ffmpeg-android.sh armv7     # 32-бит (armeabi-v7a, API 25)
#   tools/build-ffmpeg-android.sh arm64     # 64-бит (arm64-v8a, API 25)
#   tools/build-ffmpeg-android.sh all
#
# Результат: artifacts/ffmpeg-android-<arch>/{ffmpeg,ffprobe}
# Промежуточные файлы: .build-ffmpeg/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SDK_DIR="${ANDROID_HOME:-}"
if [ -z "$SDK_DIR" ] && [ -f local.properties ]; then
    SDK_DIR="$(sed -n 's/^sdk\.dir=//p' local.properties)"
fi
NDK="${ANDROID_NDK_HOME:-$SDK_DIR/ndk/27.0.12077973}"
API="${API:-25}"
JF_TAG="${JF_TAG:-v7.1.1-2}"
X264_TAG="${X264_TAG:-stable}"

WORK="$ROOT/.build-ffmpeg"
SRC="$WORK/src"
ART="$ROOT/artifacts"

[ -d "$NDK" ] || { echo "NDK не найден: $NDK" >&2; exit 1; }
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
[ -d "$TOOLCHAIN" ] || { echo "Нет тулчейна в $NDK" >&2; exit 1; }

fetch_sources() {
    mkdir -p "$SRC"
    if [ ! -d "$SRC/jellyfin-ffmpeg-$JF_TAG" ]; then
        echo "== исходники jellyfin-ffmpeg $JF_TAG =="
        curl -fsSL -o "$SRC/jf.tar.gz" \
            "https://github.com/jellyfin/jellyfin-ffmpeg/archive/refs/tags/${JF_TAG}.tar.gz"
        tar -xzf "$SRC/jf.tar.gz" -C "$SRC"
    fi
    if [ ! -d "$SRC/x264" ]; then
        echo "== исходники x264 ($X264_TAG) =="
        git clone --depth 1 --branch "$X264_TAG" https://code.videolan.org/videolan/x264.git "$SRC/x264"
    fi
}

arch_params() {
    case "$1" in
        armv7)
            ARCH=arm; CPU=armv7-a; TRIPLE=arm-linux-androideabi
            CC_BIN="$TOOLCHAIN/bin/armv7a-linux-androideabi${API}-clang"
            ;;
        arm64)
            ARCH=aarch64; CPU=armv8-a; TRIPLE=aarch64-linux-android
            CC_BIN="$TOOLCHAIN/bin/aarch64-linux-android${API}-clang"
            ;;
        *) echo "неизвестная архитектура: $1" >&2; exit 1 ;;
    esac
    SYSROOT="$TOOLCHAIN/sysroot"
    AR="$TOOLCHAIN/bin/llvm-ar"
    RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
    STRIP="$TOOLCHAIN/bin/llvm-strip"
    NM="$TOOLCHAIN/bin/llvm-nm"
    CXX="$CC_BIN++"
    [ -x "$CXX" ] || CXX="$CC_BIN"
    PREFIX="$WORK/prefix-$1"
}

build_x264() {
    local arch="$1"
    arch_params "$arch"
    local build="$WORK/build-x264-$arch"
    rm -rf "$build"; mkdir -p "$build" "$PREFIX"
    cp -r "$SRC/x264/." "$build/"
    pushd "$build" >/dev/null
    echo "== x264 ($arch) =="
    CC="$CC_BIN" CXX="$CXX" AR="$AR" RANLIB="$RANLIB" \
    ./configure --host="$TRIPLE" --sysroot="$SYSROOT" \
        --prefix="$PREFIX" \
        --enable-static --disable-shared --enable-pic \
        --disable-cli --disable-opencl \
        --extra-cflags="-fPIC -O2" >"$WORK/x264-$arch.log" 2>&1 \
        || { tail -30 "$WORK/x264-$arch.log"; exit 1; }
    make -j"$(nproc)" >>"$WORK/x264-$arch.log" 2>&1 \
        || { tail -30 "$WORK/x264-$arch.log"; exit 1; }
    make install >>"$WORK/x264-$arch.log" 2>&1
    popd >/dev/null
}

build_ffmpeg() {
    local arch="$1"
    arch_params "$arch"
    local build="$WORK/build-ffmpeg-$arch"
    local out="$ART/ffmpeg-android-$arch"
    rm -rf "$build"; mkdir -p "$build" "$out"
    cp -r "$SRC/jellyfin-ffmpeg-$JF_TAG/." "$build/"
    pushd "$build" >/dev/null
    echo "== ffmpeg ($arch) =="
    PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig" \
    ./configure \
        --prefix="$PREFIX" \
        --target-os=android --arch="$ARCH" --cpu="$CPU" \
        --enable-cross-compile --sysroot="$SYSROOT" \
        --cc="$CC_BIN" --cxx="$CXX" --ar="$AR" --ranlib="$RANLIB" --strip="$STRIP" --nm="$NM" \
        --enable-static --disable-shared --enable-pic \
        --enable-gpl --enable-libx264 \
        --enable-jni --enable-mediacodec \
        --enable-ffmpeg --enable-ffprobe --disable-ffplay \
        --disable-doc --disable-debug \
        --extra-cflags="-I$PREFIX/include -fPIC -O2" \
        --extra-ldflags="-L$PREFIX/lib" \
        >"$WORK/ffmpeg-$arch.log" 2>&1 \
        || { tail -40 "$WORK/ffmpeg-$arch.log"; exit 1; }
    make -j"$(nproc)" >>"$WORK/ffmpeg-$arch.log" 2>&1 \
        || { tail -40 "$WORK/ffmpeg-$arch.log"; exit 1; }
    cp -f ffmpeg ffprobe "$out"/
    "$STRIP" --strip-all "$out/ffmpeg" "$out/ffprobe"
    popd >/dev/null
    echo "готово: $out"
    ls -lh "$out"
}

fetch_sources
case "${1:-all}" in
    armv7) build_x264 armv7; build_ffmpeg armv7 ;;
    arm64) build_x264 arm64; build_ffmpeg arm64 ;;
    all)   build_x264 armv7; build_ffmpeg armv7; build_x264 arm64; build_ffmpeg arm64 ;;
    *) echo "usage: $0 [armv7|arm64|all]" >&2; exit 1 ;;
esac
