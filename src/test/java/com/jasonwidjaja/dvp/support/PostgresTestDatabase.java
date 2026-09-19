package com.jasonwidjaja.dvp.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class PostgresTestDatabase {

    public static final String IMAGE = "postgres:18.6";

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(IMAGE);

    static {
        POSTGRES.start();
    }

    private PostgresTestDatabase() {
    }

    public static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
