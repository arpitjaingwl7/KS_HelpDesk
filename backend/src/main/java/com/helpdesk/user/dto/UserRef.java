package com.helpdesk.user.dto;

import java.util.UUID;

/** The smallest public view of a user: id and display name. */
public record UserRef(UUID id, String fullName) {
}
