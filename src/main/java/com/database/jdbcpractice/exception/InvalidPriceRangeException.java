package com.database.jdbcpractice.exception;

public class InvalidPriceRangeException extends RuntimeException {
    // 가격 관련 오류는 min>max, 음수 가격 등 여러 종류이기 때문에 message를 인자로 주입받는다
    public InvalidPriceRangeException(String message) {
        super(message );
    }
}
