package com.seatbook.service;

import com.seatbook.error.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

final class SeatLabels {
    private SeatLabels() {}

    /** Trim + upper-case, reject blanks/duplicates, return SORTED (the deterministic lock order). */
    static List<String> normalize(List<String> raw) {
        if (raw == null || raw.isEmpty()) throw ApiException.badRequest("seats required");
        TreeSet<String> out = new TreeSet<>();
        for (String s : raw) {
            if (s == null || s.isBlank() || s.length() > 32 || s.contains(","))
                throw ApiException.badRequest("invalid seat label");
            if (!out.add(s.trim().toUpperCase())) throw ApiException.badRequest("duplicate seat in request");
        }
        return new ArrayList<>(out);
    }
}
