package com.helpdesk.organization.dto;

import java.util.UUID;

public record OrganizationRef(UUID id, String name, String slug) {
}
