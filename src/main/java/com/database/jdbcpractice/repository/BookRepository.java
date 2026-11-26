package com.database.jdbcpractice.repository;

import com.database.jdbcpractice.model.Book;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class BookRepository {

    private final JdbcTemplate jdbc;

    public BookRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existsCategory(String name) {
        Integer count = jdbc.queryForObject(
                "select count(*) from category where LOWER(name)=LOWER(?)",
                Integer.class,
                name
        );

        return count > 0;
    }

    public List<Book> findBooks(
            String category,
            double minPrice,
            double maxPrice
    ) {

        String sql = """
            SELECT b.title, b.price, b.rating, b.availability, b.url, c.name AS category
            FROM book b
            JOIN category c ON b.category_id = c.id
            WHERE LOWER(c.name) = LOWER(?)
              AND b.price BETWEEN ? AND ?
            ORDER BY b.price
            """;

        return jdbc.query(sql,
                (rs, rowNum) -> new Book(
                        rs.getString("title"),
                        rs.getBigDecimal("price"),
                        rs.getInt("rating"),
                        rs.getString("availability"),
                        rs.getString("url"),
                        rs.getString("category")),
                category, minPrice, maxPrice);
    }

    public void deleteAll() {
        jdbc.execute("truncate book, category restart identity");
    }
}
