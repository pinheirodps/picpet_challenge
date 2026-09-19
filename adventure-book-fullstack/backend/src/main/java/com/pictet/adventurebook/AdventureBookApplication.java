package com.pictet.adventurebook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Adventure Book backend. Run with {@code mvn spring-boot:run} — no
 * external services required, H2 persists to a local file and Angular is served separately
 * in development.
 */
@SpringBootApplication
public class AdventureBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdventureBookApplication.class, args);
    }
}
