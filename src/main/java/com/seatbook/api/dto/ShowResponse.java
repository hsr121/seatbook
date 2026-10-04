package com.seatbook.api.dto;

import com.seatbook.domain.ShowState;
import java.util.List;

public record ShowResponse(String id, String name, long pricePaise, int perUserLimit, int totalSeats,
                           int available, int held, int confirmed, List<ShowState.SeatStatus> seats) {
    public static ShowResponse of(ShowState s) {
        var m = s.meta();
        return new ShowResponse(m.id(), m.name(), m.pricePaise(), m.perUserLimit(), s.seats().size(),
                s.available(), s.held(), s.confirmed(), s.seats());
    }
}
