# 온라인 서점 정보 조회 웹프로그램

[books.toscrape.com](https://books.toscrape.com/)의 카테고리별 도서 정보를 jsoup으로 크롤링해 Neon PostgreSQL에 저장하고, REST API로 조회하는 Spring Boot 애플리케이션입니다.

## 개발 환경

| 항목 | 버전 |
|---|---|
| IDE | IntelliJ IDEA 2026.1.4 |
| JDK | 21 (OpenJDK 21.0.11) |
| Maven | 3.10.1 (Maven Wrapper) |
| Spring | Spring Boot 4.2.0 (Spring Framework 7.1.0) |
| 기타 | jsoup 1.24.1, PostgreSQL JDBC 42.7.14, Neon PostgreSQL |

## 실행 방법

1. DB 접속 정보는 `src/main/resources/application-secret.yaml`에 있습니다.
2. 실행합니다.
   ```bash
   ./mvnw spring-boot:run
   ```
3. DB가 비어 있으면 첫 실행에서 크롤링이 진행되며 약 1~2분 걸립니다.
   **콘솔에 `크롤링 완료: 카테고리 50개, 책 1000권 저장` 로그가 나온 뒤에 API를 호출하세요.**
   웹 서버는 크롤링보다 먼저 요청을 받기 시작하는데, 크롤링이 끝나기 전에는 데이터가 아직 보이지 않아 `존재하지 않는 카테고리입니다`가 반환됩니다.

## 크롤링 동작 방식

앱이 시작되면 `schema.sql`로 테이블(`category`, `book`)을 만든 뒤, `BookScraper`가 자동으로 실행됩니다.

1. **DB가 비어 있을 때만 크롤링합니다.**
   books.toscrape.com의 데이터는 바뀌지 않으므로 최초 1회만 수집합니다. 이미 데이터가 있으면 크롤링을 건너뛰어 앱이 바로 시작됩니다.
   - 크롤링 시: `크롤링 시작` → `크롤링 완료: 카테고리 50개, 책 1000권 저장` 로그 출력
   - 건너뛸 시: `이미 책 1000권이 저장되어 있어 크롤링을 건너뜁니다` 로그 출력
2. **크롤링은 jsoup으로만 수행합니다.**
   메인 페이지 사이드바에서 카테고리 50개를 가져오고, 카테고리마다 "next" 링크가 없어질 때까지 모든 페이지를 돌며 책 정보를 파싱합니다. DB에 데이터를 수동으로 넣지 않습니다.
3. **카테고리 단위로 묶어서 저장합니다.**
   책을 한 권씩 INSERT하면 DB 왕복이 1000번 이상 생겨 느리므로, 카테고리 하나의 책을 모아 `batchUpdate` 한 번으로 저장합니다.
4. **크롤링 전체가 하나의 트랜잭션입니다.**
   도중에 실패하면 저장된 내용까지 모두 롤백됩니다. 일부만 저장된 채 남지 않으므로, 다음 실행에서 처음부터 다시 크롤링합니다.
5. **다시 크롤링하려면** `DELETE /books`로 데이터를 지운 뒤 앱을 재시작합니다.

## API

### `GET /books`

특정 카테고리에서 가격 범위(이상~이하) 안의 책을 가격 오름차순으로 반환합니다.

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| category | 문자열 | O | 카테고리명 (대소문자 무시) |
| minPrice | 실수 | O | 최소 가격 |
| maxPrice | 실수 | O | 최대 가격 |

**요청 예시**
```
GET /books?category=Travel&minPrice=10&maxPrice=30
```

**응답 예시**
```json
[
  {
    "title": "The Road to Little Dribbling: Adventures of an American in Britain (Notes From a Small Island #2)",
    "price": 23.21,
    "rating": 1,
    "availability": "In stock",
    "url": "https://books.toscrape.com/catalogue/the-road-to-little-dribbling-adventures-of-an-american-in-britain-notes-from-a-small-island-2_277/index.html",
    "category": "Travel"
  },
  {
    "title": "1,000 Places to See Before You Die",
    "price": 26.08,
    "rating": 5,
    "availability": "In stock",
    "url": "https://books.toscrape.com/catalogue/1000-places-to-see-before-you-die_1/index.html",
    "category": "Travel"
  }
]
```

### `DELETE /books`

크롤링이 다시 동작하는지 확인하기 위한 API입니다. 저장된 카테고리와 책을 모두 삭제하며, 앱을 재시작하면 다시 크롤링합니다.
인증 없이 전체 데이터를 지우므로 과제 확인 용도로만 사용합니다.

**요청 예시**
```bash
curl -X DELETE http://localhost:8080/books
```

**응답 예시**
```json
{"message": "모든 데이터를 삭제했습니다"}
```

### 오류 응답

모든 오류는 `{"message": "..."}` 형식의 JSON으로 반환합니다.

| 상황 | 상태 코드 | message |
|---|---|---|
| 존재하지 않는 카테고리 | 404 | 존재하지 않는 카테고리입니다 |
| minPrice > maxPrice | 400 | minPrice가 maxPrice보다 높아서는 안됩니다 |
| 음수 가격 | 400 | 가격은 0 이상이어야 합니다 |
| 필수 파라미터 누락 | 400 | {파라미터}는 필수 파라미터입니다 |
| 가격이 숫자가 아님 | 400 | {파라미터}는 숫자여야 합니다 |

## 소스 코드 설명

### scraper
- `BookScraper`: 앱 시작 직후 자동 실행되는 `ApplicationRunner`입니다. 위 "크롤링 동작 방식"대로 jsoup으로 도서 정보를 수집해 DB에 저장합니다.

### model
- `Book`: 책 한 권의 정보(제목, 가격, 별점, 재고, URL, 카테고리명)를 담는 record입니다. API 응답 JSON의 형태가 됩니다.

### repository
- `BookRepository`: `JdbcTemplate`으로 DB를 조회합니다.
  - `existsCategory(name)`: `COUNT(*)`로 해당 이름의 카테고리가 있는지 확인합니다.
  - `findBooks(category, minPrice, maxPrice)`: `book`과 `category`를 JOIN해 `price BETWEEN ? AND ?`(양 끝 포함)로 조회하고, 가격 오름차순으로 정렬합니다. `RowMapper`로 각 행을 `Book`으로 변환합니다.
  - `deleteAll()`: `TRUNCATE`로 `book`, `category`를 비웁니다. (`DELETE /books`에서 사용)
  - 카테고리 이름은 `LOWER()`로 대소문자를 무시합니다.

### service
- `BookService`: 요청 값을 검증한 뒤 조회를 수행합니다.
  1. 가격이 음수인지 확인합니다.
  2. minPrice가 maxPrice보다 큰지 확인합니다.
  3. 카테고리가 존재하는지 확인합니다.

  모두 통과하면 Repository로 해당 카테고리와 가격 범위의 책을 조회합니다. 전체 데이터 삭제도 담당합니다.

### controller
- `BookController`: `@RestController`입니다.
  - `@GetMapping("/books")`: `category`, `minPrice`, `maxPrice`를 필수 파라미터로 받아 `BookService`에 전달하고, 결과를 JSON으로 반환합니다.
  - `@DeleteMapping("/books")`: 전체 데이터를 삭제합니다.

### response
- `MessageResponse`: `{"message": "..."}` 형태의 성공 응답 record입니다. (`DELETE /books`에서 사용)

### exception
- `CategoryNotFoundException`: 존재하지 않는 카테고리일 때 던지는 예외입니다.
- `InvalidPriceRangeException`: 가격 범위가 잘못되었을 때 던지는 예외입니다. 상황별 메시지를 인자로 받습니다.
- `ErrorResponse`: `{"message": "..."}` 형태의 오류 응답 record입니다.
- `GlobalExceptionHandler`: `@RestControllerAdvice`로 모든 예외를 한 곳에서 받아 상태 코드와 `ErrorResponse`로 변환합니다.

### resources
- `schema.sql`: `category`, `book` 테이블을 생성합니다. `spring.sql.init.mode=always`로 앱 시작 시 자동 실행되며, `IF NOT EXISTS`로 여러 번 실행해도 안전합니다.
- `application.yaml`: datasource 설정입니다. 실제 접속 정보는 `application-secret.yaml`에서 불러옵니다.
