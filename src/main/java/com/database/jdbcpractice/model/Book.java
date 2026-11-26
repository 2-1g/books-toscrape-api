package com.database.jdbcpractice.model;

import java.math.BigDecimal;

public record Book(
        String title,
        BigDecimal price,
        int rating,
        String availability,
        String url,
        String category
) {
}
