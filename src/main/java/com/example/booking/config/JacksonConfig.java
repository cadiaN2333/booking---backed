package com.example.booking.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 统一时间格式，避免 LocalDateTime 默认输出成 2026-09-10T10:00:00 这种前端不友好的形式 */
@Configuration
public class JacksonConfig {

  private static final String DATE_TIME = "yyyy-MM-dd HH:mm:ss";
  private static final String DATE = "yyyy-MM-dd";
  private static final String TIME = "HH:mm";

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
    return builder ->
        builder
                //出参时间格式
            .serializers(new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_TIME)))
            .serializers(new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE)))
            .serializers(new LocalTimeSerializer(DateTimeFormatter.ofPattern(TIME)))
                //入参时间格式
            .deserializers(new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DATE_TIME)))
            .deserializers(new LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE)))
            .deserializers(new LocalTimeDeserializer(DateTimeFormatter.ofPattern(TIME)));
  }
}
