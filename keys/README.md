# Ключи подписи

`freekeys.jks` — ключ, которым подписывались релизы старого клиента
(скопирован из `TS_old/keystore/freekeys.jks`), алиас `freekeys`.

## Как подписывается

`./build.sh` берёт пароль в таком порядке:

1. `keys/keystore.properties` (если файл есть и в нём заполнен `storePassword`);
2. переменная окружения `KS_PASS` (и `KEY_PASS`, `KEY_ALIAS`);
3. спрашивает в консоли.

Пример `keys/keystore.properties` (создать вручную, в git не нужен):

```properties
storePassword=пароль_keystore
keyAlias=freekeys
keyPassword=пароль_ключа
```

Сборка без пароля (только debug): `./build.sh --debug`.

## Имя файла

Release: `app/build/outputs/apk/release/TorrServe_Silo.X.Client.apk`,
debug: `.../debug/TorrServe_Silo.X.Client-debug.apk`, где `X` — `versionName`
из `app/build.gradle` (`versionCode`/`versionName` меняются вручную).

## Сборка релиза APK в GitHub Actions

Тег вида `Silo.N.Client` (например `Silo.1.Client`) — при пуше такого тега
workflow `.github/workflows/release-apk.yml` собирает подписанный APK
(`TorrServe_Silo.1.Client.apk`), считает SHA256 и создаёт релиз с этими файлами.

Номер после `Silo.` становится `versionCode`, тег целиком — `versionName`,
поэтому в `app/build.gradle` версию для релиза руками менять не нужно.

Секреты репозитория (Settings → Secrets and variables → Actions):

| Секрет | Что положить |
|---|---|
| `KEYSTORE_BASE64` | результат `base64 -w0 keys/freekeys.jks` |
| `KEYSTORE_PASSWORD` | пароль хранилища |
| `KEY_PASSWORD` | пароль ключа (если отличается) |
| `KEY_ALIAS` | необязательно, по умолчанию `freekeys` |

Сгенерировать base64 локально:

```bash
base64 -w0 keys/freekeys.jks
```

Проверить локально ту же сборку, что делает CI:

```bash
KS_PASS='пароль' KEY_PASS='пароль' ./build.sh -PversionName=Silo.1.Client -PversionCode=1
```
