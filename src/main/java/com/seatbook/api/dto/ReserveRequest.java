package com.seatbook.api.dto;

import java.util.List;

/** No user_id field on purpose: identity comes from the token. Unknown body fields are ignored. */
public record ReserveRequest(List<String> seats, String idempotencyKey) {}
