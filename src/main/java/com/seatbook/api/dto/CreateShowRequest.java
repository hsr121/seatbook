package com.seatbook.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record CreateShowRequest(@NotBlank String name, @NotEmpty List<String> seats,
                                @PositiveOrZero long pricePaise, Integer perUserLimit) {}
