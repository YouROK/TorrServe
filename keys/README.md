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
