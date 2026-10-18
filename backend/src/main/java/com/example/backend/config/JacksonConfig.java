package com.example.backend.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            JavaTimeModule javaTimeModule = new JavaTimeModule();

            // Custom Serializer: Outputs format yyyy-MM-dd HH:mm:ss
            javaTimeModule.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
                @Override
                public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                    if (value == null) {
                        gen.writeNull();
                    } else {
                        gen.writeString(value.format(DATE_TIME_FORMATTER));
                    }
                }
            });

            // Custom Flexible Deserializer: Accepts yyyy-MM-dd HH:mm:ss, ISO-8601, and date-only yyyy-MM-dd
            javaTimeModule.addDeserializer(LocalDateTime.class, new JsonDeserializer<>() {
                @Override
                public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                    String text = p.getText();
                    if (text == null || text.trim().isEmpty()) {
                        return null;
                    }
                    text = text.trim();

                    // 1. Format: yyyy-MM-dd HH:mm:ss
                    try {
                        return LocalDateTime.parse(text, DATE_TIME_FORMATTER);
                    } catch (DateTimeParseException ignored) {}

                    // 2. Format: ISO-8601 (yyyy-MM-dd'T'HH:mm:ss)
                    try {
                        return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } catch (DateTimeParseException ignored) {}

                    // 3. Format: Date only (yyyy-MM-dd) -> default to 00:00:00
                    try {
                        return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
                    } catch (DateTimeParseException ignored) {}

                    throw new IOException("Cannot parse date-time: '" + text + "'. Expected format: yyyy-MM-dd HH:mm:ss (e.g. 2026-10-18 20:00:00)");
                }
            });

            builder.modules(javaTimeModule);
        };
    }
}
