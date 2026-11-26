package com.database.jdbcpractice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.database.jdbcpractice.model.Book;
import com.database.jdbcpractice.repository.BookRepository;

/**
 * Neon에 저장된 크롤링 데이터로 BookRepository를 확인한다.
 * (book 테이블이 비어 있으면 앱 시작 시 크롤링부터 하므로 오래 걸린다.)
 */
@SpringBootTest
class BookRepositoryTest {

	@Autowired
	BookRepository repository;

	@Test
	void existsCategory() {
		assertThat(repository.existsCategory("Travel")).isTrue();
		assertThat(repository.existsCategory("travel")).isTrue();   // 대소문자 무시
		assertThat(repository.existsCategory("Nope")).isFalse();
	}

	@Test
	void findBooksInPriceRange() {
		List<Book> books = repository.findBooks("Travel", new BigDecimal("10"), new BigDecimal("30"));

		assertThat(books).extracting(Book::price)
				.containsExactly(new BigDecimal("23.21"), new BigDecimal("26.08"));   // 가격 오름차순
		assertThat(books).allSatisfy(book -> assertThat(book.category()).isEqualTo("Travel"));
		assertThat(books.get(0).title()).startsWith("The Road to Little Dribbling");
		assertThat(books.get(0).rating()).isEqualTo(1);
	}

	@Test
	void findBooksIncludesBoundaryPrices() {
		// BETWEEN은 양 끝을 포함한다: 정확히 23.21 ~ 26.08로 조회해도 두 권 모두 나와야 한다.
		List<Book> books = repository.findBooks("Travel", new BigDecimal("23.21"), new BigDecimal("26.08"));
		assertThat(books).hasSize(2);
	}

	@Test
	void findBooksReturnsEmptyListWhenNoBookInRange() {
		assertThat(repository.findBooks("Travel", new BigDecimal("0"), new BigDecimal("1"))).isEmpty();
	}

	@Test
	void findBooksIgnoresCategoryCase() {
		// existsCategory("travel")가 true라면, 같은 이름으로 책도 찾아져야 한다.
		assertThat(repository.findBooks("travel", new BigDecimal("10"), new BigDecimal("30"))).hasSize(2);
	}
}
