package io.niko.ui.probe

/**
 * The UI probe (docs/research/ui-probe.md): the canvas's Главная · Простой (P-Simple) with fixed values, drawn once
 * with Views ([ProbeViewsActivity]) and once with Compose ([ProbeComposeActivity]) so that the emulator measures what
 * each costs (.github/scripts/ui-probe.sh). Both show these and nothing live: no service, no database. Started only by
 * the shell (the activities ask for android.permission.DUMP, which adb's shell holds and no app does).
 */
internal object ProbeContent {
    const val WORDMARK = "niko"
    const val SIMPLE = "Простой"
    const val ADVANCED = "Расширенный"
    const val DISCONNECT = "Отключить"
    const val STATUS = "Подключено"
    const val DETAIL = "00:42:17 · ↓ 4.2 MB/s · ↑ 312 KB/s"
    const val CONNECTION_LABEL = "Подключение"
    const val CONNECTION_VALUE = "naive · HTTPS"
    const val MODE_LABEL = "Режим"
    const val MODE_VALUE = "VPN"
    const val ROUTE_LABEL = "Маршрут"
    const val ROUTE_VALUE = "Россия"
    const val NODE = "arngory"
    const val NODE_UNTIL = "до 19 ноября · "
    const val NODE_TRAFFIC = "38 / 100 GB"
    const val NODE_PING = "38 ms"
}
