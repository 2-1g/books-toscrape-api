-- 앱 시작 시 실행된다 (spring.sql.init.mode=always). 여러 번 실행해도 안전하다.

CREATE TABLE IF NOT EXISTS category (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    url  TEXT         NOT NULL
);

CREATE TABLE IF NOT EXISTS book (
    id           SERIAL PRIMARY KEY,
    category_id  INT           NOT NULL REFERENCES category (id),
    title        TEXT          NOT NULL,
    price        NUMERIC(10, 2) NOT NULL,
    rating       SMALLINT,
    availability VARCHAR(50),
    url          TEXT          NOT NULL UNIQUE
);

-- GET /books 조회 조건(category, price 범위)에 맞춘 인덱스
CREATE INDEX IF NOT EXISTS idx_book_category_price ON book (category_id, price);
