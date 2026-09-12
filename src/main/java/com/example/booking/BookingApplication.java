package com.example.booking;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.example.booking.mapper")
@SpringBootApplication
public class BookingApplication {

  public static void main(String[] args) {
    SpringApplication.run(BookingApplication.class, args);
  }
}
