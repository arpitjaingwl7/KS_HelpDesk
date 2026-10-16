package com.helpdesk.ticket.domain;

/** Declared in business order; the database sorts by the generated {@code priority_rank} column. */
public enum TicketPriority {
    LOW, MEDIUM, HIGH, URGENT
}
