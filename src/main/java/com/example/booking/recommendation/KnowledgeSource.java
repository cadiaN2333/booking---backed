package com.example.booking.recommendation;

import java.time.LocalDateTime;
import java.time.LocalTime;

/** 从交易库读取的公开场馆知识源，不包含用户和实时库存信息。 */
public record KnowledgeSource(
    Long venueId,
    String venueName,
    String venueAddress,
    Long courtId,
    String courtName,
    String courtType,
    Integer price,
    LocalTime openTime,
    LocalTime closeTime,
    Integer status,
    String facilities,
    String rules,
    LocalDateTime updatedAt) {}
