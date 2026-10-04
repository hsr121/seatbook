package com.seatbook.error;

/** A domain outcome (409), never a server error. Extends RuntimeException so @Transactional rolls back. */
public class DeclineException extends RuntimeException {
    private final Reason reason;
    public DeclineException(Reason reason) {
        super(reason.label, null, false, false);   // no stack trace: declines are hot-path and cheap
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
