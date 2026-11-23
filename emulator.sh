#!/usr/bin/env bash
# Запуск эмулятора Android без Android Studio (окно + звук).
#
#   ./emulator.sh                 - окно и звук, AVD по умолчанию Pixel_8_Pro
#   ./emulator.sh --cold          - полная загрузка (без снапшота), если быстрый старт сломался
#   ./emulator.sh --headless      - без окна (агент, SSH, скриншоты через adb)
#   ./emulator.sh --no-audio      - без звука
#   ./emulator.sh -a Small_Phone  - другой AVD (список: ./emulator.sh --list)
#   ./emulator.sh --gpu host|swiftshader_indirect|angle_indirect
#   ./emulator.sh -p 5556         - фиксированный порт adb
#   ./emulator.sh --restart       - перезапустить, если этот AVD уже работает
#   ./emulator.sh --detach        - уйти в фон, лог в .shots/emulator-<avd>.log
#   ./emulator.sh --no-accel      - без KVM (очень медленно, для отладки самого эмулятора)
#
# Звук выводится через PulseAudio/PipeWire (-audio pulse). Для x86/x86_64-образов
# нужен /dev/kvm: без него эмулятор не стартует, скрипт подскажет, как это исправить.
set -euo pipefail

cd "$(dirname "$0")"

AVD="Pixel_8_Pro"
GPU=""
PORT=""
AUDIO=1
HEADLESS=0
COLD=0
DETACH=0
RESTART=0
ACCEL=1

usage() {
    sed -n '2,16p' "$0" | sed -e 's/^# \{0,1\}//' -e '/^$/d'
}

detect_sdk() {
    local c
    for c in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}"; do
        if [ -n "$c" ] && [ -x "$c/emulator/emulator" ]; then
            echo "$c"
            return 0
        fi
    done
    if [ -f local.properties ]; then
        c="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
        if [ -n "$c" ] && [ -x "$c/emulator/emulator" ]; then
            echo "$c"
            return 0
        fi
    fi
    for c in "$HOME/Android/Sdk" /usr/lib/android-sdk /opt/android-sdk; do
        if [ -x "$c/emulator/emulator" ]; then
            echo "$c"
            return 0
        fi
    done
    return 1
}

while [ $# -gt 0 ]; do
    case "$1" in
        -a|--avd)      AVD="${2:?не задано имя AVD}"; shift 2 ;;
        --gpu)         GPU="${2:?не задан режим GPU}"; shift 2 ;;
        -p|--port)     PORT="${2:?не задан порт}"; shift 2 ;;
        --headless)    HEADLESS=1; shift ;;
        --no-audio)    AUDIO=0; shift ;;
        --cold)        COLD=1; shift ;;
        --detach)      DETACH=1; shift ;;
        --restart)     RESTART=1; shift ;;
        --no-accel)    ACCEL=0; shift ;;
        --list)
            SDK="$(detect_sdk 2>/dev/null || true)"
            if [ -z "$SDK" ]; then
                echo "Ошибка: Android SDK не найден (SDK=/emulator/emulator)" >&2
                exit 1
            fi
            echo "Доступные AVD:"
            "$SDK/emulator/emulator" -list-avds
            exit 0
            ;;
        -h|--help)     usage; exit 0 ;;
        *)             echo "Неизвестный аргумент: $1" >&2; usage >&2; exit 1 ;;
    esac
done

SDK="$(detect_sdk || true)"
if [ -z "$SDK" ]; then
    echo "Ошибка: Android SDK с эмулятором не найден." >&2
    echo "  Укажите путь: export ANDROID_SDK_ROOT=/путь/к/Sdk" >&2
    exit 1
fi
EMU="$SDK/emulator/emulator"
ADB="$SDK/platform-tools/adb"
[ -x "$ADB" ] || ADB="$(command -v adb || true)"
if [ ! -x "$ADB" ]; then
    echo "Ошибка: adb не найден ни в $SDK/platform-tools, ни в PATH." >&2
    exit 1
fi

if ! "$EMU" -list-avds 2>/dev/null | grep -qx "$AVD"; then
    echo "Ошибка: AVD '$AVD' не найден. Доступны:" >&2
    "$EMU" -list-avds | sed 's/^/  /' >&2
    exit 1
fi

# Уже запущенный экземпляр этого же AVD (второй старт невозможен).
running_serial_for() {
    local s name
    while read -r s _; do
        case "$s" in emulator-*) ;; *) continue ;; esac
        name="$("$ADB" -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r')"
        if [ "$name" = "$1" ]; then
            echo "$s"
            return 0
        fi
    done < <("$ADB" devices 2>/dev/null | tail -n +2)
    return 1
}

if SERIAL="$(running_serial_for "$AVD")"; then
    if [ "$RESTART" -eq 0 ]; then
        echo "Эмулятор '$AVD' уже запущен: $SERIAL"
        echo "  Перезапустить: $0 --restart"
        echo "  Остановить:    $ADB -s $SERIAL emu kill"
        exit 0
    fi
    echo "Останавливаю $SERIAL..."
    "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true
    for _ in $(seq 1 40); do
        if ! "$ADB" devices 2>/dev/null | grep -q "^$SERIAL"; then
            break
        fi
        sleep 0.5
    done
fi

if [ "$HEADLESS" -eq 0 ] && [ -z "${DISPLAY:-}" ] && [ -z "${WAYLAND_DISPLAY:-}" ]; then
    echo "Ошибка: нет графической сессии (DISPLAY/WAYLAND_DISPLAY пусты)." >&2
    echo "  Для запуска без окна используйте: $0 --headless" >&2
    exit 1
fi

if [ "$ACCEL" -eq 1 ] && { [ ! -r /dev/kvm ] || [ ! -w /dev/kvm ]; }; then
    echo "Ошибка: /dev/kvm недоступен, а образ '$AVD' — x86/x86_64." >&2
    echo "  Без KVM такой образ не стартует. Варианты:" >&2
    echo "    1) добавить пользователя в группу kvm и перезайти в сессию:" >&2
    echo "         sudo usermod -aG kvm $USER" >&2
    echo "    2) запустить без ускорения (очень медленно): $0 --no-accel" >&2
    exit 1
fi

if [ "$AUDIO" -eq 1 ]; then
    PULSE_SOCK="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/pulse/native"
    if [ ! -S "$PULSE_SOCK" ]; then
        echo "ВНИМАНИЕ: не найден сокет PulseAudio/PipeWire ($PULSE_SOCK)." >&2
        echo "  Звука не будет; проверьте pipewire-pulse или запустите с --no-audio." >&2
    fi
fi

if [ -z "$GPU" ]; then
    if [ "$HEADLESS" -eq 1 ]; then
        GPU="swiftshader_indirect"
    else
        GPU="auto"
    fi
fi
case "$GPU" in
    swiftshader) GPU="swiftshader_indirect" ;;
esac

ARGS=(-avd "$AVD" -gpu "$GPU" -netdelay none -netspeed full -no-metrics)
if [ "$HEADLESS" -eq 1 ]; then
    ARGS+=(-no-window)
fi
if [ "$AUDIO" -eq 1 ]; then
    ARGS+=(-audio pulse)
else
    ARGS+=(-no-audio)
fi
if [ "$COLD" -eq 1 ]; then
    ARGS+=(-no-snapshot)
fi
if [ "$ACCEL" -eq 0 ]; then
    ARGS+=(-accel off)
fi
if [ -n "$PORT" ]; then
    ARGS+=(-port "$PORT")
fi

# Кэш эмулятора и быстрая вспомогательная отладка.
export ANDROID_SDK_ROOT="$SDK"
export ANDROID_HOME="${ANDROID_HOME:-$SDK}"

print_ready() {
    local serial
    serial="$(running_serial_for "$AVD" || true)"
    [ -n "$serial" ] || return 0
    echo
    echo "Эмулятор готов: $serial ($AVD)"
    echo "  adb -s $serial shell                 # шелл внутри Android"
    echo "  ./build.sh --debug && $ADB -s $serial install -r app/build/outputs/apk/debug/app-debug.apk"
    echo "  $ADB -s $serial exec-out screencap -p > .shots/screen.png"
    echo "  $ADB -s $serial emu kill             # выключить"
}

if [ "$DETACH" -eq 1 ]; then
    mkdir -p .shots
    LOG=".shots/emulator-$AVD.log"
    echo "Запускаю '$AVD' в фоне (окно: $([ "$HEADLESS" -eq 1 ] && echo нет || echo да), звук: $([ "$AUDIO" -eq 1 ] && echo да || echo нет), gpu: $GPU)"
    echo "  лог: $LOG"
    nohup "$EMU" "${ARGS[@]}" >"$LOG" 2>&1 &
    EMU_PID=$!
    disown "$EMU_PID" 2>/dev/null || true
    for _ in $(seq 1 120); do
        if ! kill -0 "$EMU_PID" 2>/dev/null; then
            echo "Ошибка: эмулятор завершился при старте, хвост лога:" >&2
            tail -n 25 "$LOG" >&2
            exit 1
        fi
        SERIAL="$(running_serial_for "$AVD" || true)"
        [ -n "$SERIAL" ] && break
        sleep 1
    done
    if [ -z "${SERIAL:-}" ]; then
        echo "Ошибка: за 120 с эмулятор не появился в adb, лог: $LOG" >&2
        exit 1
    fi
    "$ADB" -s "$SERIAL" wait-for-device
    for _ in $(seq 1 180); do
        if [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
            break
        fi
        sleep 1
    done
    print_ready
    exit 0
fi

echo "Запускаю '$AVD' (окно: $([ "$HEADLESS" -eq 1 ] && echo нет || echo да), звук: $([ "$AUDIO" -eq 1 ] && echo да || echo нет), gpu: $GPU)"
echo "  быстрый старт из снапшота: $([ "$COLD" -eq 1 ] && echo нет || echo да), ускорение KVM: $([ "$ACCEL" -eq 1 ] && echo да || echo нет)"
echo "  остановить: Ctrl+C или adb -s emulator-<порт> emu kill"
echo
exec "$EMU" "${ARGS[@]}"
