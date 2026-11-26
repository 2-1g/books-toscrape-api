package com.database.jdbcpractice.controller;

import com.database.jdbcpractice.model.Book;
import com.database.jdbcpractice.service.BookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/books")
    public List<Book> getAllBooks(
            @RequestParam String category,
            @RequestParam double minPrice,
            @RequestParam double maxPrice
    ) {
        return bookService.findBooks(category, minPrice, maxPrice);
    }
}
