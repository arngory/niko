# Подпись и выпуск релизов

Релизные APK собирает и подписывает GitHub Actions (`.github/workflows/release.yml`).
Ключ хранится только в секретах репозитория, в git его нет.

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

В репозитории: **Settings → Secrets and variables → Actions → New repository secret**. Создать четыре секрета:

| Секрет | Значение |
|---|---|
| `KEYSTORE_BASE64` | содержимое `niko-release.jks.b64` |
| `KEYSTORE_PASS` | пароль хранилища |
| `ALIAS_NAME` | `niko` (alias из шага 1) |
| `ALIAS_PASS` | пароль ключа (для PKCS12 тот же, что `KEYSTORE_PASS`) |

То же самое через `gh`:

```bash
gh secret set KEYSTORE_BASE64 < niko-release.jks.b64
gh secret set KEYSTORE_PASS
gh secret set ALIAS_NAME --body niko
gh secret set ALIAS_PASS
```

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

Workflow проверит, что тег совпадает с `VERSION_NAME`, соберёт libcore и APK, подпишет их, посчитает
`SHA256SUMS` и создаст GitHub Release. Теги с дефисом (`v1.5.0-beta.1`) публикуются как pre-release.
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

2. Содержимое `niko-update.key` положить в секрет `MINISIGN_SECRET_KEY` (`gh secret set MINISIGN_SECRET_KEY < niko-update.key`).
   Резервную копию `niko-update.key` хранить офлайн, как и ключ APK.
3. `niko-update.pub` закоммитить в корень репозитория. Сборки с ним проверяют обновления этим ключом.

Release workflow подписывает `SHA256SUMS` и проверяет подпись этим ключом до публикации. Если `niko-update.pub`
есть, а секрета нет, релиз падает: такие обновления приложение отвергло бы. Приложение проверяет обновления тем
ключом, с которым его собрали. Поэтому после смены ключа сборки со старым ключом новый релиз не примут, и их
пользователям придётся один раз обновиться со страницы релиза.

