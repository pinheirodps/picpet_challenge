package com.pictet.adventurebook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.Locale;

/**
 * Pins the request locale to English, so bean validation messages
 * ({@code @NotBlank}, {@code @NotEmpty}, etc.) come back in English regardless of the
 * server's system locale — consistent with the rest of this English-only API.
 */
@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        return new FixedLocaleResolver(Locale.ENGLISH);
    }
}
