package com.seatbook.api;

import com.seatbook.api.dto.CreateShowRequest;
import com.seatbook.api.dto.ReservationResponse;
import com.seatbook.api.dto.ReserveRequest;
import com.seatbook.api.dto.ShowResponse;
import com.seatbook.domain.ReserveResult;
import com.seatbook.service.ReservationService;
import com.seatbook.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** The one controller for show + reservation management. Thin: no transactions, no entities, DTOs only. */
@RestController
public class BookingController {
    private final ShowService shows;
    private final ReservationService reservations;

    public BookingController(ShowService shows, ReservationService reservations) {
        this.shows = shows; this.reservations = reservations;
    }

    @PostMapping("/shows")                                   // admin (SecurityConfig)
    ResponseEntity<ShowResponse> createShow(@Valid @RequestBody CreateShowRequest req) {
        var s = shows.create(req.name(), req.seats(), req.pricePaise(), req.perUserLimit());
        return ResponseEntity.status(HttpStatus.CREATED).body(ShowResponse.of(s));
    }

    @GetMapping("/shows/{id}")
    ShowResponse getShow(@PathVariable String id) { return ShowResponse.of(shows.state(id)); }

    @PostMapping("/shows/{id}/reserve")
    ResponseEntity<ReservationResponse> reserve(@PathVariable String id,
                                                @AuthenticationPrincipal Jwt jwt,   // identity = token subject only
                                                @RequestHeader(value = "Idempotency-Key", required = false) String headerKey,
                                                @RequestBody ReserveRequest body) {
        String key = headerKey != null ? headerKey : body.idempotencyKey();
        ReserveResult r = reservations.reserve(jwt.getSubject(), id, body.seats(), key);
        return ResponseEntity.status(r.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(r.replayed()))
                .body(ReservationResponse.of(r.reservation()));
    }

    @PostMapping("/reservations/{id}/cancel")
    ReservationResponse cancel(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ReservationResponse.of(reservations.cancel(jwt.getSubject(), id));
    }
}
