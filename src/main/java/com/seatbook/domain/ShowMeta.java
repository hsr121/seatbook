package com.seatbook.domain;

/** Small, per-request DTO. Never holds the seat list. */
public record ShowMeta(String id, String name, long pricePaise, int perUserLimit) {}
