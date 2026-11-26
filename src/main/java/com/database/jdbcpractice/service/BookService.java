package com.database.jdbcpractice.service;

import com.database.jdbcpractice.exception.CategoryNotFoundException;
import com.database.jdbcpractice.exception.InvalidPriceRangeException;
import com.database.jdbcpractice.model.Book;
import com.database.jdbcpractice.repository.BookRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BookService {

    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = repository;
    }

    public List<Book> findBooks(
            String category,
            double minPrice,
            double maxPrice
    ) {
        if (minPrice < 0 || maxPrice < 0) {
            throw new InvalidPriceRangeException("가격은 0 이상이어야 합니다");
        }

        if (minPrice > maxPrice) {
            throw new InvalidPriceRangeException("minPrice가 maxPrice보다 높아서는 안됩니다");
        }

        if (!repository.existsCategory(category)) {
            throw new CategoryNotFoundException();
        }

        return repository.findBooks(category, minPrice, maxPrice);
    }
}
