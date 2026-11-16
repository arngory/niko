# Подпись и выпуск релизов

Релизные APK собирает и подписывает GitHub Actions (`.github/workflows/release.yml`).
Ключи хранятся только в секретах окружения `release`, в git их нет. Собирает APK задача без ключей: Gradle, его
плагины и зависимости ключей не видят. Подписывает отдельная задача `sign` (`.github/scripts/release-sign.sh`),
в которой код сборки не выполняется: APK и `SHA256SUMS` подписываются в ней под одно одобрение владельца.

## 1. Создать ключ (один раз)

Нужен JDK (`keytool` входит в него). Создаём PKCS12-хранилище с RSA-ключом на ~27 лет:

```bash
keytool -genkeypair -v \
  -keystore niko-release.jks -storetype PKCS12 \
  -alias niko -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=niko"
```

`keytool` спросит пароль. В PKCS12 пароль хранилища и пароль ключа совпадают, поэтому
`KEYSTORE_PASS` и `ALIAS_PASS` ниже будут одинаковыми.

**Сразу сделай резервную копию** `niko-release.jks` и пароля (менеджер паролей, офлайн-носитель).
Без этого ключа обновления не встанут поверх уже установленного приложения: придётся удалять
приложение с потерей настроек и выпускать его заново с новым ключом.

Отпечаток сертификата (его стоит опубликовать в README или в описании релиза, чтобы пользователи
могли сверить):

```bash
keytool -list -v -keystore niko-release.jks -alias niko | grep SHA256
```

## 2. Положить ключ в секреты GitHub

Закодировать хранилище в base64 одной строкой:

```bash
base64 -w0 niko-release.jks > niko-release.jks.b64   # Linux
base64 -i niko-release.jks -o niko-release.jks.b64   # macOS
```

Секреты кладутся в окружение `release`, а не в секреты репозитория: секрет репозитория читает workflow любой
ветки, а секрет окружения — только задача, которой это окружение разрешило запуск. Имена у них свои (`RELEASE_*`),
таких секретов в репозитории нет, поэтому задача подписи никогда не возьмёт секрет репозитория вместо секрета
окружения: нет секрета в окружении — релиз падает.

**Порядок важен: всё это — до первого релиза с этим workflow.** Если релиз запустится раньше, чем создано
окружение, GitHub создаст его сам, без ревьюера и без ограничений.

1. Окружение: **Settings → Environments → New environment** `release`:
   - **Required reviewers** — владелец, один; **Prevent self-reviews** выключен (релиз запускает и одобряет он
     сам; появится второй ревьюер — включить);
   - **Deployment branches and tags** — «Selected»: тег `v*` и ветка `main`.
2. Правило на теги: **Settings → Rules → Rulesets → New tag ruleset**, цель `v*`, запрещены создание, изменение
   и удаление, в обходе (bypass) — только владелец. Окружение пускает любой тег `v*`, а workflow берётся из
   коммита тега: тег на коммите непроверенной ветки привёл бы её код к ключам за одно ошибочное одобрение.
   Проверка «коммит на `main`» в release.yml ловит ошибки, но её код — тоже из коммита тега; держит правило.
3. Секреты окружения `release` (**Environment secrets → Add secret**):

| Секрет | Значение |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | содержимое `niko-release.jks.b64` |
| `RELEASE_KEYSTORE_PASS` | пароль хранилища |
| `RELEASE_ALIAS_NAME` | `niko` (alias из шага 1) |
| `RELEASE_ALIAS_PASS` | пароль ключа (для PKCS12 тот же, что `KEYSTORE_PASS`) |
| `RELEASE_MINISIGN_KEY` | ключ обновлений (§5) |

То же самое через `gh`:

```bash
gh secret set RELEASE_KEYSTORE_BASE64 --env release < niko-release.jks.b64
gh secret set RELEASE_KEYSTORE_PASS --env release
gh secret set RELEASE_ALIAS_NAME --env release --body niko
gh secret set RELEASE_ALIAS_PASS --env release
gh secret set RELEASE_MINISIGN_KEY --env release < niko-update.key
```

4. **Обязательно** удалить прежние секреты репозитория: `KEYSTORE_BASE64`, `KEYSTORE_PASS`, `ALIAS_NAME`,
   `ALIAS_PASS`, `MINISIGN_SECRET_KEY` (`gh secret delete KEYSTORE_BASE64` и так далее). Workflow их больше не
   читает, но любой workflow любой ветки по-прежнему мог бы.

Задача `sign` проверяет, что APK подписаны сертификатом релизов (SHA-256
`f4ac83655f2e6be36235706303bc4e4a1dd7bb36fcc2285dc526f9df5636c13f`, `EXPECT_CERT_SHA256` в release.yml): другой
ключ в секрете релиз не выпустит. Сборки CI ключей не видят и выходят неподписанными; эмуляторные задачи
подписывают их одноразовым ключом, а задача `sign-check` прогоняет тот же скрипт подписи на одноразовых ключах.

После этого удали `niko-release.jks.b64`: это тот же ключ, только в другой кодировке.

## 3. Выпустить релиз

1. В `niko.properties` поднять `VERSION_NAME` и `VERSION_CODE`. `VERSION_CODE` должен строго расти,
   иначе Android не даст обновиться.
2. Закоммитить и запушить.
3. Поставить тег, совпадающий с `VERSION_NAME`, и запушить его:

   ```bash
   git tag v1.5.0
   git push origin v1.5.0
   ```

Тег можно поставить и на сайте (Releases → Draft a new release → новый тег на `main`): сайт при этом сразу создаёт
пустой релиз, а workflow потом кладёт в него файлы, заголовок и заметки из `.github/release-notes/<тег>.md`.

Workflow проверит, что тег совпадает с `VERSION_NAME` и коммит лежит на `main`, соберёт libcore и APK без ключей,
затем задача `sign` попросит одобрения владельца (Actions → запуск → **Review deployments**), подпишет APK и
`SHA256SUMS`, а задача публикации проверит подпись и создаст GitHub Release. Теги с дефисом (`v1.5.0-beta.1`) публикуются как pre-release.
Если секретов нет, релизный workflow падает, а не выкладывает неподписанные APK.

Ещё релиз можно запустить вручную: **Actions → Release → Run workflow**, указав уже существующий тег.

## 4. Локальная подпись (необязательно)

В `local.properties` (он в `.gitignore`):

```properties
KEYSTORE_FILE=/абсолютный/путь/niko-release.jks
KEYSTORE_PASS=...
ALIAS_NAME=niko
ALIAS_PASS=...
```

Затем `./gradlew app:assembleOssRelease`.

## Проверка подписи APK

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs NekoBox-*-arm64-v8a.apk
```

SHA-256 сертификата должен совпадать с отпечатком из шага 1.

## Про имя пакета

Форк собирается с `PACKAGE_NAME=io.niko` (`niko.properties`), поэтому ставится рядом с оригинальным
NekoBox (`moe.nb4a`) и с ним не конфликтует. Настройки из оригинала автоматически не переносятся:
экспортируй их через резервную копию в старом приложении и импортируй в новом.

Не меняй `PACKAGE_NAME` после первого публичного релиза: для Android это будет другое приложение, и
обновление поверх не встанет. По той же причине `VERSION_CODE` должен только расти.

## 5. Ключ обновлений (minisign)

Сборка с GitHub (flavor `oss`) ставит свои обновления сама. Она скачивает `SHA256SUMS` релиза и его подпись
`SHA256SUMS.minisig`, проверяет подпись публичным ключом из `niko-update.pub` и сверяет trusted comment
`niko <тег>`. Затем скачивает APK для своего ABI, сверяет его SHA-256 и отдаёт системному установщику (Android 7.0 и
новее: установщик 6.0 не принимает файл по адресу content://, там остаётся страница релиза).
Пока в корне репозитория нет `niko-update.pub`, приложение по-прежнему открывает страницу релиза, и в сборке нет
разрешения на установку приложений (`REQUEST_INSTALL_PACKAGES`): его манифест `app/src/updates` подключается только
вместе с ключом.

1. Создать ключ без пароля (его прочтёт workflow):

   ```bash
   minisign -G -W -p niko-update.pub -s niko-update.key
   ```

2. Содержимое `niko-update.key` положить в секрет `RELEASE_MINISIGN_KEY` окружения `release`
   (`gh secret set RELEASE_MINISIGN_KEY --env release < niko-update.key`), не в секреты репозитория.
   В файле две строки: комментарий и сам ключ. Надёжнее класть командой из файла; при вставке в форму на сайте
   нужны обе строки. Если в секрет попала одна строка (без перевода строки или только ключ), задача подписи с
   v1.57.1 разберёт и её; в 1.57.0 она падала с «Error while loading the secret key file».
   Резервную копию `niko-update.key` хранить офлайн, как и ключ APK.
3. `niko-update.pub` закоммитить в корень репозитория. Сборки с ним проверяют обновления этим ключом.

Задача `sign` в окружении `release` подписывает `SHA256SUMS`, задача публикации без ключей проверяет подпись этим
ключом до публикации. Если `niko-update.pub`
есть, а секрета нет, релиз падает: такие обновления приложение отвергло бы. Приложение проверяет обновления тем
ключом, с которым его собрали. Поэтому после смены ключа сборки со старым ключом новый релиз не примут, и их
пользователям придётся один раз обновиться со страницы релиза.

