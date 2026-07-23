# ShopApp

Тестовый интернет-магазин: Android-витрина и REST API над готовой SQLite-базой.

## Стек

- Android: Kotlin, Jetpack Compose, Coroutines, StateFlow, Retrofit.
- Backend: Kotlin, Ktor, kotlinx.serialization, SQLite JDBC.
- Данные: `server/data/test_task.db` (локальный файл, не коммитится).

## Требования

- macOS или Linux, JDK 21;
- Android SDK с API 35, `adb` в `PATH`;
- запущенный Android-эмулятор;
- `curl`. Отдельно устанавливать Gradle не нужно — используется wrapper.

## Запуск

Всё одной командой:

```shell
./run.sh
```

Скрипт запускает сервер, ждёт `/health`, собирает и устанавливает APK на
эмулятор, затем открывает приложение. Пока он работает, сервер доступен на
`localhost:8080`; Android обращается к нему через `http://10.0.2.2:8080`.
Остановка — `Ctrl+C`.

Отдельно сервер:

```shell
./gradlew :server:run
curl http://localhost:8080/health
```

Отдельно Android:

```shell
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.shopapp/.MainActivity
```

Путь к базе можно переопределить через `SHOPAPP_DB_PATH`. Для другого адреса
API при сборке используется `-PSHOP_API_BASE_URL=http://host:port/`.

## API

- `GET /health`
- `GET /api/categories`
- `GET /api/products?category=<id>&search=<text>`
- `GET /api/products/{id}`
- `POST /api/orders`
- `GET /api/orders/{id}`
- `GET /api/admin/stats?from=YYYY-MM-DD&to=YYYY-MM-DD`

## Правила расчётов

- Деньги в БД и API — `Long` в копейках; Compose форматирует их в рубли.
- `percent` считается от subtotal, `fixed` хранится в рублях и умножается на
  100. Проверяются активность, срок, минимальная сумма и лимит использований;
  скидка не превышает subtotal. Невалидный код не отменяет заказ.
- Имя и цена позиции сохраняются в `order_items` как snapshot. Суммы заказа и
  статистика используют только `order_items.price`, не текущую цену товара.
- Текущий статус — последняя запись `order_status_history` по
  `datetime(created_at) DESC, id DESC`; кэшированное поле заказа не используется.
- Revenue включает только `paid`, `shipped`, `delivered` и уменьшается на
  скидку. `ordersCount` исключает `cancelled`, средний чек делит revenue на
  число оплаченных заказов, топ-5 считается по snapshot-выручке.

Старая схема не хранит snapshot параметров промокода и готовую сумму скидки.
Поэтому для старых заказов скидка восстанавливается по сохранённому коду и
текущей строке `promocodes`, но дата валидности проверяется относительно
`orders.created_at`. Если промокод позднее изменили, восстановленная сумма
может отличаться от фактически применённой в прошлом.

## Экраны

- Каталог: категории, серверный поиск и фильтр, карточки, количество через
  `+`/`−`, форма заказа без отдельной корзины.
- Статистика: период `from/to`, revenue, число заказов, средний чек и топ-5.
- Между экранами — простая нижняя навигация.

## Готово

Каталог, создание и просмотр заказа, промокоды, транзакционное уменьшение
остатков, статистика, SQL-сверка `scripts/check_stats.sql`, unit/integration и
телефонные Compose-тесты. Ошибки API возвращают понятные `400/404/409`.

## Не сделано

Авторизация, изменение статусов, полноценная админка, пагинация, offline/cache
и отдельная корзина. Для тестового объёма данных они не требуются.

## Почему так

Без ORM и лишних интерфейсов: небольшие JDBC repository-классы и
`PreparedStatement` проще проверить по исходной схеме. Заказ выполняется одной
транзакцией. На Android один repository и ViewModel со StateFlow на экран —
достаточно для небольшого приложения и прозрачно для ревью.

Проверки:

```shell
./gradlew :server:test :app:assembleDebug
sqlite3 -readonly server/data/test_task.db < scripts/check_stats.sql
```
