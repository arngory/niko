package io.niko.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import io.niko.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The Niko dialog's card as images, light and dark, over the scrim: the preview build's notice (P-Preview), the
 * missing plugin (P-NoPlugin), a question, an invite's PIN, the exit address's services.
 */
@RunWith(Parameterized::class)
class NikoDialogSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 412 * 3,
            screenHeight = 915 * 3,
            xdpi = 480,
            ydpi = 480,
            density = Density.XXHIGH,
            locale = "ru",
            nightMode = if (mode == "dark") NightMode.NIGHT else NightMode.NOTNIGHT,
        ),
    )

    private fun shot(spec: NikoDialogSpec) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") {
            Box(Modifier.fillMaxSize().background(Niko.colors.surface0)) {
                Box(Modifier.fillMaxSize().background(Niko.colors.scrim).padding(NikoSpace.s6), contentAlignment = Alignment.Center) {
                    NikoDialogCard(spec, { _, _, _ -> }, {})
                }
            }
        }
    }

    @Test
    fun preview() = shot(
        NikoDialogSpec(
            icon = R.drawable.ic_l_triangle_alert,
            title = "Предварительная версия 1.68.0-pre1",
            message = "Эта сборка для проверки: в ней возможны ошибки. О найденном напишите в Issues на GitHub.",
            buttons = listOf(NikoDialogButton("Сообщить об ошибке"), NikoDialogButton("Понятно", primary = true)),
        ),
    )

    @Test
    fun noPlugin() = shot(
        NikoDialogSpec(
            icon = R.drawable.ic_l_plug,
            title = "Нужен плагин Trojan-Go",
            message = "Профиль Франкфурт требует установки подключаемого модуля Trojan-Go, но он не найден.",
            items = listOf("F-Droid", "Google Play", "Скачать по ссылке"),
            buttons = listOf(NikoDialogButton("Отмена")),
        ),
    )

    @Test
    fun question() = shot(
        NikoDialogSpec(
            title = "Импорт профиля",
            message = "Импортировать профиль Франкфурт?",
            buttons = listOf(NikoDialogButton("Отмена"), NikoDialogButton("Да", primary = true)),
        ),
    )

    @Test
    fun pin() = shot(
        NikoDialogSpec(
            title = "PIN для node.example.org",
            message = "Приглашению нужен PIN: 6 цифр, которые администратор сообщил отдельно от приглашения.\n\nНеверный PIN: осталось попыток: 4.",
            field = NikoDialogField(hint = "000000", digits = 6),
            buttons = listOf(NikoDialogButton("Отмена"), NikoDialogButton("ОК", primary = true)),
        ),
    )

    @Test
    fun choices() = shot(
        NikoDialogSpec(
            title = "IP выхода",
            message = "Сервис узнает адрес узла.",
            choices = listOf("Cloudflare (cloudflare.com)", "ipinfo.io", "ipwho.is", "ipapi.co"),
            chosen = 1,
            buttons = listOf(NikoDialogButton("Отмена"), NikoDialogButton("Спросить", primary = true)),
        ),
    )

    @Test
    fun danger() = shot(
        NikoDialogSpec(
            title = "Удалить узел?",
            message = "Профили узла и его ключ на этом устройстве будут удалены.",
            buttons = listOf(NikoDialogButton("Отмена"), NikoDialogButton("Удалить", danger = true)),
        ),
    )
}
