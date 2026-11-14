#!/usr/bin/env bash
# Сборка клиента TorrServe Silo.
#
#   ./build.sh           - release APK (подписывается ключом keys/freekeys.jks)
#   ./build.sh --debug   - debug APK
#
# Пароль keystore берётся из keys/keystore.properties, переменной KS_PASS
# или спрашивается в консоли. После сборки печатается путь к APK.
set -euo pipefail

cd "$(dirname "$0")"

usage() {
    echo "usage: $0 [--debug]"
}

MODE="release"
EXTRA_ARGS=()
for arg in "$@"; do
    case "$arg" in
        --debug) MODE="debug" ;;
        --release) MODE="release" ;;
        -h|--help) usage; exit 0 ;;
        -P*) EXTRA_ARGS+=("$arg") ;;
        *) usage; exit 1 ;;
    esac
done

export ANDROID_USER_HOME="$PWD/.android-home"
export GRADLE_USER_HOME="$PWD/.gradle-home"

GRADLE_ARGS=()

if [ "$MODE" = "release" ]; then
    if [ -f keys/keystore.properties ] && grep -q '^storePassword=.\+' keys/keystore.properties; then
        echo "Подпись: пароль keystore из keys/keystore.properties"
    else
        if [ -z "${KS_PASS:-}" ]; then
            if [ ! -t 0 ]; then
                echo "Ошибка: не задан пароль keystore (KS_PASS или keys/keystore.properties)" >&2
                exit 1
            fi
            read -rsp "Пароль keystore (keys/freekeys.jks): " KS_PASS
            echo
        fi
        if [ -z "${KEY_PASS:-}" ]; then
            if [ -t 0 ]; then
                read -rsp "Пароль ключа (Enter - как у keystore): " KEY_PASS
                echo
            fi
            [ -n "${KEY_PASS:-}" ] || KEY_PASS="$KS_PASS"
        fi
        GRADLE_ARGS+=(-PstorePassword="$KS_PASS" -PkeyPassword="$KEY_PASS" -PkeyAlias="${KEY_ALIAS:-freekeys}")
    fi
    TASK=":app:assembleRelease"
    OUT_DIR="app/build/outputs/apk/release"
else
    TASK=":app:assembleDebug"
    OUT_DIR="app/build/outputs/apk/debug"
fi

./gradlew "$TASK" --no-daemon "${GRADLE_ARGS[@]}" "${EXTRA_ARGS[@]}"

APK=""
for candidate in $(ls -1t "$OUT_DIR"/*.apk 2>/dev/null || true); do
    APK="$candidate"
    break
done
if [ -z "$APK" ]; then
    echo "Ошибка: APK не найден в $OUT_DIR" >&2
    exit 1
fi
APK_ABS="$(realpath "$APK")"

echo
echo "Готово:"
echo "  $APK_ABS"

if [ "$MODE" = "release" ]; then
    SDK_DIR="${ANDROID_HOME:-}"
    if [ -z "$SDK_DIR" ] && [ -f local.properties ]; then
        SDK_DIR="$(sed -n 's/^sdk\.dir=//p' local.properties)"
    fi
    APKSIGNER=""
    if [ -n "$SDK_DIR" ]; then
        APKSIGNER="$(ls "$SDK_DIR"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
    fi
    [ -n "$APKSIGNER" ] || APKSIGNER="$(command -v apksigner || true)"
    if [ -n "$APKSIGNER" ]; then
        if "$APKSIGNER" verify --print-certs "$APK_ABS" >/dev/null 2>&1; then
            echo "  Подпись: OK"
        else
            echo "  ВНИМАНИЕ: APK не подписан" >&2
        fi
    fi
fi
