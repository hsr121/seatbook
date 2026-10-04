package com.seatbook.domain;

public record CancelOutcome(Reservation reservation, int released) {}
