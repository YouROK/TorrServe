# Источники обновлений

Клиент проверяет две вещи при открытии главного окна: версию **сервера** и версию
**приложения**. Проверка идёт не чаще раза в 5 минут (один запрос на всё).

## Почему не API GitHub напрямую

На части ТВ-сетей (и на тестовой приставке это подтвердилось) провайдер кеширует
ответы `github.com` и `api.github.com`: клиент получал список релизов, которому
уже сутки, при свежем заголовке `Date` и даже при метке времени в URL. Прямая загрузка
файлов при этом работает — страдает только «список версий».

Поэтому основной источник версий — два JSON-файла на `raw.githubusercontent.com`
(этот хост отдаёт актуальные данные), а лента GitHub осталась запасным вариантом.

## `server_release.json`

```json
{
  "version": "Silo.2",
  "links": {
    "android-arm7":  "https://github.com/YouROK/TorrServer/releases/download/Silo.2/TorrServer-android-arm7",
    "android-arm64": "https://github.com/YouROK/TorrServer/releases/download/Silo.2/TorrServer-android-arm64",
    "android-386":   "https://github.com/YouROK/TorrServer/releases/download/Silo.2/TorrServer-android-386",
    "android-amd64": "https://github.com/YouROK/TorrServer/releases/download/Silo.2/TorrServer-android-amd64"
  }
}
```

Клиент берёт `version` (для сравнения с версией из `/api/system/version`) и ссылку
под ABI устройства (`android-arm7`, `android-arm64`, `android-386`, `android-amd64`).

## `apk_release.json`

```json
[
  {
    "version": "Silo.1.Client",
    "versionInt": 1,
    "link": "https://github.com/YouROK/TorrServer/releases/download/Silo.1.Client/TorrServe_Silo.1.Client.apk",
    "desc": "Первый релиз Silo-клиента"
  }
]
```

Берётся запись с максимальным `versionInt` (при равенстве — по номеру в `version`).
`version` должен начинаться с `Silo.`, иначе запись игнорируется (так отсекаются
старые `MatriX.*`).

## Порядок выпуска

1. Поставить тег и запушить его:

   ```bash
   git tag Silo.1.Client && git push origin Silo.1.Client
   ```

2. Workflow `.github/workflows/release-apk.yml` соберёт `TorrServe_Silo.1.Client.apk`,
   подпишет ключом из секретов и создаст релиз.
3. Добавить в `apk_release.json` запись про этот релиз (`version`, `versionInt`, `link`).
4. Для сервера — обновить `version` и `links` в `server_release.json`.

Проверка версии идёт по JSON, поэтому обновление увидится даже там, где GitHub
в списке релизов отдаёт кеш.
