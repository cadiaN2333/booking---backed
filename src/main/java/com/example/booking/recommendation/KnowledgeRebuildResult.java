package com.example.booking.recommendation;

/** 单个场地知识文档重建结果。 */
public record KnowledgeRebuildResult(String documentKey, String contentHash, int writtenCount) {}
