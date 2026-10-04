package com.seatbook.domain;

import java.util.List;

/** Counts are derived from the same row set as the seat list, so available+held+confirmed==total by construction. */
public record ShowState(ShowMeta meta, int available, int held, int confirmed, List<SeatStatus> seats) {
    public record SeatStatus(String seat, String status) {}
}
