# ShopApp

Минимальный Kotlin Gradle-проект:

- `app` — Android-приложение на Jetpack Compose;
- `server` — Ktor REST API с SQLite JDBC и kotlinx.serialization.

Локальная база данных находится в `server/data/test_task.db` и не добавляется в Git.

Запуск сервера:

```shell
./gradlew :server:run
```

Проверка:

```shell
curl http://localhost:8080/health
```

Запуск сервера и Android-приложения на уже запущенном эмуляторе одной командой:

```shell
./run.sh
```

Для `run.sh` нужны `adb` в `PATH`, `curl` и подключённый эмулятор. Android-приложение
обращается к серверу хоста по адресу `http://10.0.2.2:8080`.

REST API:

- `GET /api/products?category=<id>&search=<text>`
- `GET /api/products/{id}`
- `GET /api/categories`
- `POST /api/orders`
- `GET /api/orders/{id}`
- `GET /api/admin/stats?from=YYYY-MM-DD&to=YYYY-MM-DD`

Все денежные поля API имеют тип `Long` и передаются в копейках. Путь к локальной
SQLite-базе можно переопределить переменной окружения `SHOPAPP_DB_PATH`.
