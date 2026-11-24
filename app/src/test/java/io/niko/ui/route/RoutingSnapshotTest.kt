package io.niko.ui.route

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import io.niko.ui.compose.NikoTheme
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The routing screens as images, light and dark, on the canvas's data: the rules (P-Rules), a rule and one of its
 * pages (P-Rule), the route check with its answer and with «Ещё» open (P-Check), the rule sets (P-Lists).
 */
@RunWith(Parameterized::class)
class RoutingSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")
    }

    @get:Rule
    val paparazzi = Paparazzi(
        // the canvas's artboard: 412 by 915, in Russian
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

    private fun shot(content: @androidx.compose.runtime.Composable () -> Unit) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") { content() }
    }

    private val rulesNone = object : RulesActions {
        override fun back() {}
        override fun guide() {}
        override fun add() {}
        override fun edit(id: Long) {}
        override fun enable(id: Long, on: Boolean) {}
        override fun move(from: Int, to: Int) {}
        override fun moved() {}
        override fun delete(id: Long) {}
        override fun preset(preset: RulePreset) {}
        override fun lists() {}
        override fun check() {}
        override fun reset() {}
    }

    private val ruleNone = object : RuleActions {
        override fun back() {}
        override fun save() {}
        override fun delete() {}
        override fun pickApps() {}
        override fun pickProfile() {}
        override fun editJson() {}
    }

    private val checkNone = object : CheckActions {
        override fun back() {}
        override fun form(form: CheckForm) {}
        override fun more(open: Boolean) {}
        override fun run() {}
    }

    private val listsNone = object : ListsActions {
        override fun back() {}
        override fun import() {}
        override fun updateAll() {}
        override fun update(name: String) {}
        override fun delete(name: String) {}
    }

    @Test
    fun rules() = shot {
        RulesScreen(
            RulesState(
                listOf(
                    RuleRow(1, "Сбербанк Онлайн, Госуслуги", 2, emptyList(), RuleOut.DIRECT, null, true),
                    RuleRow(2, "Реклама и трекеры", 0, listOf("geosite:category-ads-all"), RuleOut.BLOCK, null, true),
                    RuleRow(3, "Заблокированное в России — через прокси", 0, listOf("geosite-ru-blocked", "geoip-ru-blocked"), RuleOut.PROXY, null, true),
                    RuleRow(4, "Торренты", 0, listOf("protocol: bittorrent"), RuleOut.DIRECT, null, false),
                    RuleRow(5, "Работа", 0, listOf("corp.example +2"), RuleOut.PROFILE, "arngory · naive", true),
                ),
                RulePreset.RUSSIA,
                "24.11.2026",
            ),
            rulesNone,
        )
    }

    private val banks = RuleForm(
        name = "Банки напрямую", packages = listOf("ru.sberbankmobile", "ru.rostel"), outbound = -1L, dpiBypass = true,
        port = "443", network = "tcp",
    )

    @Test
    fun rule() = shot {
        RuleScreen(
            banks,
            RuleInfo(existing = true, labels = mapOf("ru.sberbankmobile" to "Сбербанк Онлайн", "ru.rostel" to "Госуслуги")),
            RulePage.MAIN, {}, {}, ruleNone,
        )
    }

    @Test
    fun newRule() = shot {
        RuleScreen(RuleForm(outbound = 12L), RuleInfo(existing = false, profileName = "Helsinki · REALITY"), RulePage.MAIN, {}, {}, ruleNone)
    }

    @Test
    fun rulePorts() = shot {
        RuleScreen(banks.copy(protocol = "tls"), RuleInfo(existing = true), RulePage.PORTS, {}, {}, ruleNone)
    }

    @Test
    fun check() = shot {
        val result = CheckResult.of(
            JSONObject(
                """{"index":3,"rule":"package_name=ru.sberbankmobile","action":"route(bypass)","outbound":"bypass",
                "resolved":["194.54.14.168"],"destination":"online.sberbank.ru:443",
                "steps":[{"index":0,"rule":"","action":"sniff","note":"sniff: online.sberbank.ru"},
                         {"index":1,"rule":"protocol=dns","action":"hijack-dns"},
                         {"index":3,"rule":"package_name=ru.sberbankmobile","action":"route(bypass)"}]}"""
            )
        )
        CheckScreen(CheckState(CheckForm("online.sberbank.ru"), outcome = CheckOutcome.Done(result)), checkNone)
    }

    @Test
    fun checkMore() = shot {
        CheckScreen(
            CheckState(
                CheckForm("youtube.com", app = "Сбер", udp = true),
                more = true,
                outcome = CheckOutcome.Failed("Сначала запустите Niko: правила проверяются в работающем ядре"),
                apps = listOf("Сбербанк Онлайн (ru.sberbankmobile)", "СберМаркет (ru.instamart)", "Telegram (org.telegram.messenger)"),
            ),
            checkNone,
        )
    }

    @Test
    fun lists() = shot {
        ListsScreen(
            ListsState(
                listOf(
                    AssetRow("geoip.db", "202611180012", AssetKind.GEO),
                    AssetRow("geosite.db", "202611180012", AssetKind.GEO, updating = true),
                    AssetRow("ca.pem", "Unknown-24.11.2026", AssetKind.CA),
                    AssetRow("corp.db", "Custom", AssetKind.DB),
                )
            ),
            listsNone,
        )
    }
}
