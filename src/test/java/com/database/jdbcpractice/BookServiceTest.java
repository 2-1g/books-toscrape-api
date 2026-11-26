package com.database.jdbcpractice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.database.jdbcpractice.exception.CategoryNotFoundException;
import com.database.jdbcpractice.exception.InvalidPriceRangeException;
import com.database.jdbcpractice.model.Book;
import com.database.jdbcpractice.service.BookService;

/**
 * 오류 메시지는 과제 명세의 문구와 글자 하나까지 같아야 한다.
 */
@SpringBootTest
class BookServiceTest {

	@Autowired
	BookService service;

	@Test
	void returnsBooksForValidRequest() {
		List<Book> books = service.findBooks("Travel", 10.0, 30.0);
		assertThat(books).hasSize(2);
	}

	@Test
	void minEqualToMaxIsAllowed() {
		assertThat(service.findBooks("Travel", 23.21, 23.21)).hasSize(1);
	}

	@Test
	void throwsWhenMinGreaterThanMax() {
		assertThatThrownBy(() -> service.findBooks("Travel", 50.0, 10.0))
				.isInstanceOf(InvalidPriceRangeException.class)
				.hasMessage("minPrice가 maxPrice보다 높아서는 안됩니다");
	}

	@Test
	void throwsWhenCategoryDoesNotExist() {
		assertThatThrownBy(() -> service.findBooks("Nope", 10.0, 30.0))
				.isInstanceOf(CategoryNotFoundException.class)
				.hasMessage("존재하지 않는 카테고리입니다");
	}

	@Test
	void throwsWhenPriceIsNegative() {
		assertThatThrownBy(() -> service.findBooks("Travel", -1.0, 30.0))
				.isInstanceOf(InvalidPriceRangeException.class);
	}

	@Test
	void priceIsCheckedBeforeCategory() {
		// 카테고리도 없고 가격도 잘못됐으면, 가격 오류가 먼저 나온다.
		assertThatThrownBy(() -> service.findBooks("Nope", 50.0, 10.0))
				.isInstanceOf(InvalidPriceRangeException.class);
	}
}
