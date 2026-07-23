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
