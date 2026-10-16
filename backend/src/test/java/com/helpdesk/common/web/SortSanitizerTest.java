package com.helpdesk.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class SortSanitizerTest {

    static final Map<String, String> ALLOWED = Map.of("createdAt", "createdAt", "priority", "priorityRank");

    @Test
    void mapsApiNamesToProperties_andKeepsDirection() {
        Pageable p = SortSanitizer.sanitize(PageRequest.of(2, 10, Sort.by(Sort.Order.desc("priority"), Sort.Order.asc("createdAt"))),
                ALLOWED, Sort.by("createdAt"));
        assertThat(p.getPageNumber()).isEqualTo(2);
        assertThat(p.getSort()).containsExactly(Sort.Order.desc("priorityRank"), Sort.Order.asc("createdAt"));
    }

    @Test
    void unsorted_usesDefault() {
        Sort def = Sort.by(Sort.Direction.DESC, "createdAt");
        assertThat(SortSanitizer.sanitize(PageRequest.of(0, 20), ALLOWED, def).getSort()).isEqualTo(def);
    }

    @Test
    void unknownField_throwsInvalidSort_listingAllowedFieldsInOrder() {
        assertThatThrownBy(() -> SortSanitizer.sanitize(PageRequest.of(0, 20, Sort.by("passwordHash")), ALLOWED, Sort.unsorted()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_SORT);
                    assertThat(e.getMessage()).endsWith("Allowed: createdAt, priority");
                });
    }
}
