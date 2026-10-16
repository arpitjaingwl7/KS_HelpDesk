package com.helpdesk.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One entry of the {@code errors[]} array in a problem response. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldError(String field, String message, Object rejectedValue) {
}
