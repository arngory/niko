package io.niko.ui.admin

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import io.niko.ui.compose.NikoTheme
import io.niko.ui.settings.FieldInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The admin panel as images, light and dark: P-Admin with and without the node's warnings, the users and one of them
 * (P-AdminUser), an invite made (P-AdminInvite), the services (P-AdminServices) and one of them, the network, a page
 * being read and one that could not be, a question over a page.
 */
@RunWith(Parameterized::class)
class AdminSnapshotTest(private val mode: String) {

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

    private val none = object : AdminActions {
        override fun back() {}
        override fun refresh() {}
        override fun open(dest: AdminDest) {}
        override fun name(text: String) {}
        override fun saveName() {}
        override fun note(text: String) {}
        override fun saveNote() {}
        override fun update() {}
        override fun user(name: String) {}
        override fun newUser() {}
        override fun form(field: AdminField, text: String) {}
        override fun tick(service: String, on: Boolean) {}
        override fun createUser() {}
        override fun userEnabled(on: Boolean) {}
        override fun userService(service: String, on: Boolean) {}
        override fun invite() {}
        override fun revoke(device: DeviceRow) {}
        override fun links() {}
        override fun renew() {}
        override fun userMenu() {}
        override fun role(role: String) {}
        override fun ttl() {}
        override fun pin(on: Boolean) {}
        override fun createInvite() {}
        override fun copy() {}
        override fun share() {}
        override fun service(name: String) {}
        override fun newService() {}
        override fun serviceEnabled(name: String, on: Boolean) {}
        override fun option(key: String) {}
        override fun key(key: ServiceKey) {}
        override fun deleteService() {}
        override fun dns() {}
        override fun zones() {}
        override fun relay(name: String) {}
        override fun detail(id: String) {}
        override fun toggle(on: Boolean) {}
        override fun input(index: Int, text: String) {}
        override fun submit() {}
        override fun choose(index: Int) {}
        override fun confirm() {}
        override fun dismiss() {}
    }

    private fun shot(state: AdminState) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") { AdminScreen(state, none) }
    }

    private fun page(page: AdminPage) = AdminState("arngory", listOf(AdminPage.Status(), page))

    private val data = StatusData(
        device = "arngory/pixel · admin", host = "node.example.org", version = "v0.30.0", nodeId = "2b02f3a1c9d42025",
        name = "arngory", note = "оплата до 1 числа", users = 12, services = 6, devices = 31, spaPorts = "443, 8443, 22",
        update = UpdateRow("v0.30.0 → v0.30.1", "v0.30.1"), mirror = true, summary = false,
    )

    private val services = listOf(
        Tick("naive", "naive (naive)", true), Tick("hy2", "hy2 (hy2-masq)", true), Tick("dnstt", "dnstt (dnstt)", false),
    )

    @Test
    fun status() = shot(AdminState("arngory", listOf(AdminPage.Status(data))))

    /** The node's warnings (D-ADMIN-22), and a note typed but not sent yet. */
    @Test
    fun warnings() = shot(
        AdminState(
            "arngory",
            listOf(
                AdminPage.Status(
                    data.copy(
                        warnings = listOf("Hysteria2 hy2 has no Brutal ceiling: set up_mbps", "The certificate of node.example.org ends in 5 days"),
                        update = UpdateRow("v0.30.1"), access = listOf("203.0.113.0/24"),
                    ),
                    note = "оплата до 5 числа",
                ),
            ),
        ),
    )

    @Test
    fun users() = shot(
        page(
            AdminPage.Users(
                listOf(
                    UserRow("Мария", true, "naive, hy2 · до 19.12.2026, 13:50 · лимит 50 ГБ"),
                    UserRow("Пётр", false, "нет сервисов · бессрочно · без лимита трафика · сосед"),
                    UserRow("arngory", true, "naive, hy2, dnstt · бессрочно · без лимита трафика"),
                ),
            ),
        ),
    )

    @Test
    fun user() = shot(
        page(
            AdminPage.User(
                "Мария",
                UserInfo(true, "до 19.12.2026, 13:50 · лимит 50 ГБ", services),
                listOf(
                    DeviceRow("1", "Pixel 8", "Pixel 8 · user", "активен · зарегистрирован по приглашению · 24.11.2026, 13:50"),
                    DeviceRow("2", "Ноутбук", "Ноутбук · user", "отозван · 01.10.2026, 09:12", revocable = false),
                    DeviceRow("3", "Планшет", "Планшет · user", "приглашение, действует до 27.11.2026, 13:50", invite = true),
                ),
            ),
        ),
    )

    @Test
    fun newUser() = shot(page(AdminPage.NewUser("Мария", "30", "50", "", services)))

    @Test
    fun invite() = shot(
        page(
            AdminPage.Invite(
                "Мария", device = "Планшет", ttl = 2, pin = true,
                // an invite code that is plainly none
                result = InviteResult("RCINV2-" + "A".repeat(43) + "=", "Одноразовое приглашение для Мария/Планшет, действует до 27.11.2026, 13:50.", "471902"),
            ),
        ),
    )

    @Test
    fun inviteForm() = shot(page(AdminPage.Invite("Мария", created = true)))

    @Test
    fun services() = shot(
        page(
            AdminPage.Services(
                listOf(
                    ServiceRow("naive", "naive", true, "id 1 · 01.09.2026, 10:00"),
                    ServiceRow("Hysteria2", "hy2-masq", true, "id 2 · 01.09.2026, 10:00"),
                    ServiceRow("REALITY", "reality", true, "id 3 · 01.09.2026, 10:01"),
                    ServiceRow("dnstt", "dnstt", true, "id 4 · 02.09.2026, 18:30"),
                    ServiceRow("MasterDnsVPN", "masterdns", false, "id 5 · 02.09.2026, 18:31"),
                    ServiceRow("SSH", "ssh", true, "id 6 · 01.09.2026, 10:00"),
                ),
            ),
        ),
    )

    @Test
    fun service() = shot(
        page(
            AdminPage.Service(
                "REALITY",
                ServiceInfo(
                    ServiceRow("REALITY", "reality", true, "id 3 · 01.09.2026, 10:01"),
                    listOf(
                        OptionRow("rotate_sni", "Менять имя сервера", "true"),
                        OptionRow("open", "TCP 443 открыт для всех", "false"),
                        OptionRow("target", "Цель (ХОСТ:ПОРТ)", "www.example.com:443"),
                    ),
                    listOf(ServiceKey.REALITY),
                ),
            ),
        ),
    )

    @Test
    fun network() = shot(
        page(
            AdminPage.Network(
                NetworkData(
                    "cloudflare · example.org\nзаписей, нужных ноде: 7\n⚠ t.example.org: no NS",
                    "Последняя проверка 24.11.2026, 12:00: ok 2",
                    listOf(DetailRow("r1", "Релей r1", "198.51.100.7 · 01.09.2026, 10:00 · probe 8443")),
                ),
            ),
        ),
    )

    @Test
    fun audit() = shot(
        page(
            AdminPage.Detail(
                DetailKind.AUDIT, "Аудит",
                rows = listOf(
                    DetailRow("0", "CreateInvite Мария/Планшет", "24.11.2026, 13:50 · arngory/pixel · ok", action = false),
                    DetailRow("1", "SetServiceEnabled MasterDnsVPN", "24.11.2026, 13:41 · arngory/pixel · ok", action = false),
                    DetailRow("2", "DeleteService SSH", "24.11.2026, 13:40 · arngory/pixel · failed_precondition", action = false),
                ),
            ),
        ),
    )

    @Test
    fun update() = shot(
        page(
            AdminPage.Detail(
                DetailKind.UPDATE, "Обновление ноды",
                "Версия v0.30.0; вышел релиз v0.30.1\nОбновление по запросу: включено\nПатч-релизы сами, 03:00-05:00 (UTC)\nRelay: актуален 1",
                rows = listOf(DetailRow("to", "Обновить до v0.30.1"), DetailRow("version", "Обновить до версии…"), DetailRow("relays", "Ретрансляторы")),
            ),
        ),
    )

    @Test
    fun releases() = shot(
        page(
            AdminPage.Detail(
                DetailKind.MIRROR, "Выпуски приложения", "Включено: 1.56.0 (48 МБ)\nПроверка GitHub: 24.11.2026, 11:50",
                toggle = Tick("", "Релизы приложения на ноде", true),
            ),
        ),
    )

    @Test
    fun ports() = shot(
        page(
            AdminPage.Detail(
                DetailKind.TEXT, "Порты",
                "Что должен пропускать файрвол перед нодой.\n\nTCP 443 reality: все, постоянный\nUDP 443 hy2: все, постоянный\nTCP 22 ssh: после стука, порт sshd при установке\nUDP 20000-20100 knock: все, выбран нодой; сейчас 20042, до 14:00",
                mono = true,
            ),
        ),
    )

    @Test
    fun loading() = shot(AdminState("arngory", busy = true))

    @Test
    fun error() = shot(
        AdminState(
            "arngory", listOf(AdminPage.Status(), AdminPage.Users()),
            error = "Нода больше не знает это устройство: его отозвали, либо его пользователь отключён или истёк.",
        ),
    )

    @Test
    fun confirm() = shot(
        page(AdminPage.Users(listOf(UserRow("Мария", true, "naive, hy2 · бессрочно · без лимита трафика"))))
            .copy(sheet = AdminSheet.Confirm("Удалить пользователя Мария со всеми устройствами?")),
    )

    @Test
    fun choice() = shot(
        page(AdminPage.Invite("Мария")).copy(sheet = AdminSheet.Choice("Приглашение действует", listOf("1 час", "1 сутки", "7 дней"), 1)),
    )

    @Test
    fun input() = shot(
        page(AdminPage.Network(NetworkData("нет: записи заводятся вручную"))).copy(
            sheet = AdminSheet.Input(
                "API-токен провайдера", listOf(InputField("API-токен Cloudflare", "fake-token", FieldInput.SECRET)),
                help = "Токен Cloudflare с правами Zone.DNS:Edit и Zone.Zone:Read на зону ноды. Нода хранит его и больше не показывает.",
                error = "Нода не приняла токен",
            ),
        ),
    )
}
