package com.helpdesk.comment.dto;

import com.helpdesk.security.Role;
import java.util.UUID;

public record CommentAuthor(UUID id, String fullName, Role role) {
}
