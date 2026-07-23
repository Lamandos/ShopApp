-- Контрольный период включительно. Измените значения перед запуском при необходимости.
.parameter init
.parameter set @from "'2025-05-01'"
.parameter set @to   "'2025-07-31'"

DROP VIEW IF EXISTS temp.ranked_order_status;
CREATE TEMP VIEW ranked_order_status AS
SELECT
    order_id,
    status,
    ROW_NUMBER() OVER (
        PARTITION BY order_id
        ORDER BY datetime(created_at) DESC, id DESC
    ) AS row_number
FROM order_status_history;

-- Количество заказов по текущим статусам.
SELECT
    COALESCE(ros.status, 'new') AS current_status,
    COUNT(*) AS orders_count
FROM orders AS o
LEFT JOIN ranked_order_status AS ros
    ON ros.order_id = o.id AND ros.row_number = 1
WHERE date(o.created_at) BETWEEN date(@from) AND date(@to)
GROUP BY COALESCE(ros.status, 'new')
ORDER BY current_status;

-- Число заказов без cancelled.
SELECT
    COUNT(*) AS orders_count_without_cancelled
FROM orders AS o
LEFT JOIN ranked_order_status AS ros
    ON ros.order_id = o.id AND ros.row_number = 1
WHERE date(o.created_at) BETWEEN date(@from) AND date(@to)
  AND COALESCE(ros.status, 'new') != 'cancelled';

-- Subtotal заказов, вошедших в выручку, до применения скидок.
-- Скидки нужно вычесть по тем же правилам, что используются в PromoService.
SELECT
    COALESCE(SUM(oi.price * oi.quantity), 0) AS paid_orders_subtotal
FROM orders AS o
JOIN ranked_order_status AS ros
    ON ros.order_id = o.id AND ros.row_number = 1
JOIN order_items AS oi ON oi.order_id = o.id
WHERE date(o.created_at) BETWEEN date(@from) AND date(@to)
  AND ros.status IN ('paid', 'shipped', 'delivered');

-- Топ-5 товаров по выручке среди paid/shipped/delivered.
SELECT
    oi.product_id,
    oi.product_name,
    SUM(oi.quantity) AS quantity,
    SUM(oi.price * oi.quantity) AS revenue
FROM orders AS o
JOIN ranked_order_status AS ros
    ON ros.order_id = o.id AND ros.row_number = 1
JOIN order_items AS oi ON oi.order_id = o.id
WHERE date(o.created_at) BETWEEN date(@from) AND date(@to)
  AND ros.status IN ('paid', 'shipped', 'delivered')
GROUP BY oi.product_id, oi.product_name
ORDER BY revenue DESC, oi.product_id
LIMIT 5;

DROP VIEW temp.ranked_order_status;
