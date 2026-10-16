package com.helpdesk.common.web;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.common.error.FieldError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Restricts client-supplied sort fields to an allow-list and maps API names to entity
 * properties (e.g. {@code priority} → {@code priorityRank}). Unknown fields → {@code 400 INVALID_SORT}.
 * Page size is already capped at 100 by {@code spring.data.web.pageable.max-page-size}.
 */
public final class SortSanitizer {

    private SortSanitizer() {
    }

    public static Pageable sanitize(Pageable pageable, Map<String, String> allowed, Sort defaultSort) {
        Sort requested = pageable.getSort();
        if (requested.isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : requested) {
            String property = allowed.get(order.getProperty());
            if (property == null) {
                String names = String.join(", ", new TreeSet<>(allowed.keySet()));
                throw new ApiException(ErrorCode.INVALID_SORT,
                        "Cannot sort by '" + order.getProperty() + "'. Allowed: " + names,
                        List.of(new FieldError("sort", "must be one of " + names,
                                order.getProperty())));
            }
            orders.add(new Sort.Order(order.getDirection(), property));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }
}
