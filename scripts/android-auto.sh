#!/bin/bash
# Эмулятор Android Auto (Desktop Head Unit) для проверки экрана «Сахар» без машины.
#
#   scripts/android-auto.sh start   — открыть эмулятор (телефон подключён по USB, разблокирован,
#                                     в Android Auto запущен «сервер головного устройства»)
#   scripts/android-auto.sh stop    — закрыть эмулятор и убрать проброс порта
#   scripts/android-auto.sh status  — запущен ли эмулятор
#
# Сервер на телефоне: Настройки → Подключённые устройства → Android Auto → ⋮ →
# «Запустить сервер головного устройства» (меню разработчика — 10 нажатий на «Версия»).

set -u

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
DHU_DIR="$SDK/extras/google/auto"
PORT=5277
STATE_DIR="${TMPDIR:-/tmp}/android-auto-dhu"
PID_FILE="$STATE_DIR/dhu.pid"
LOG_FILE="$STATE_DIR/dhu.log"

running_pid() {
    [ -f "$PID_FILE" ] || return 1
    local pid
    pid=$(cat "$PID_FILE")
    kill -0 "$pid" 2>/dev/null && echo "$pid"
}

start() {
    if pid=$(running_pid); then
        echo "Эмулятор уже запущен (pid $pid). Остановить: $0 stop"
        return 0
    fi
    if [ ! -x "$DHU_DIR/desktop-head-unit" ]; then
        echo "Нет эмулятора в $DHU_DIR."
        echo "Android Studio → Settings → Android SDK → SDK Tools → «Android Auto Desktop Head Unit Emulator» (нужна версия 2.1+)."
        return 1
    fi
    if ! "$ADB" get-state >/dev/null 2>&1; then
        echo "Телефон не виден по adb. Подключите его по USB и разрешите отладку."
        return 1
    fi
    # Сервер головного устройства слушает порт на телефоне
    if ! "$ADB" shell "ss -ltn 2>/dev/null | grep -q ':$PORT '"; then
        echo "На телефоне не запущен сервер головного устройства."
        echo "Android Auto → ⋮ → «Запустить сервер головного устройства», затем повторите."
        return 1
    fi

    "$ADB" forward tcp:$PORT tcp:$PORT >/dev/null
    mkdir -p "$STATE_DIR"
    # Эмулятор читает команды из консоли и закрывается, если её нет, — держим ввод открытым
    (cd "$DHU_DIR" && nohup sh -c "tail -f /dev/null | ./desktop-head-unit" >"$LOG_FILE" 2>&1 &
     echo $! >"$PID_FILE")

    # Ждём установления связи
    for _ in $(seq 1 20); do
        if grep -q "SSL negotiation finished successfully" "$LOG_FILE" 2>/dev/null; then
            echo "Эмулятор запущен — окно Desktop Head Unit. «Сахар» — красная капля в нижней панели."
            echo "Остановить: $0 stop"
            return 0
        fi
        if grep -q "Failed to read from transport" "$LOG_FILE" 2>/dev/null; then
            stop >/dev/null
            echo "Телефон разорвал соединение. Телефон должен быть разблокирован."
            echo "Перезапустите сервер на телефоне (⋮ → остановить → запустить) и повторите. Журнал: $LOG_FILE"
            return 1
        fi
        sleep 1
    done
    echo "Эмулятор запущен, но связь с телефоном не подтвердилась за 20 с. Журнал: $LOG_FILE"
}

stop() {
    if pid=$(running_pid); then
        # Останавливаем всю группу: sh, tail и сам эмулятор
        pkill -P "$pid" 2>/dev/null
        kill "$pid" 2>/dev/null
    fi
    pkill -f "$DHU_DIR/desktop-head-unit|\./desktop-head-unit" 2>/dev/null
    # Эмулятор закрывается не мгновенно — ждём до 5 секунд, иначе добиваем
    for _ in 1 2 3 4 5; do
        pgrep -f "\./desktop-head-unit" >/dev/null || break
        sleep 1
    done
    pkill -9 -f "\./desktop-head-unit" 2>/dev/null
    rm -f "$PID_FILE"
    "$ADB" forward --remove tcp:$PORT >/dev/null 2>&1
    echo "Эмулятор остановлен."
    echo "На телефоне: Android Auto → ⋮ → «Остановить сервер головного устройства»."
}

status() {
    if pid=$(running_pid); then
        echo "Эмулятор запущен (pid $pid). Журнал: $LOG_FILE"
    else
        echo "Эмулятор не запущен."
    fi
}

case "${1:-}" in
    start) start ;;
    stop) stop ;;
    status) status ;;
    *)
        echo "Использование: $0 start|stop|status"
        exit 2
        ;;
esac
