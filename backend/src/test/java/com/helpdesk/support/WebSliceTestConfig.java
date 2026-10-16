package com.helpdesk.support;

import com.helpdesk.common.error.ProblemDetailFactory;
import com.helpdesk.config.ClockConfig;
import com.helpdesk.config.WebConfig;
import com.helpdesk.security.AuthenticatedUserConverter;
import com.helpdesk.security.ProblemAccessDeniedHandler;
import com.helpdesk.security.ProblemAuthEntryPoint;
import com.helpdesk.security.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** The real security chain and error handling for {@code @WebMvcTest} slices. */
@TestConfiguration
@Import({SecurityConfig.class, ProblemDetailFactory.class, ProblemAuthEntryPoint.class, ProblemAccessDeniedHandler.class,
        AuthenticatedUserConverter.class, ClockConfig.class, WebConfig.class})
public class WebSliceTestConfig {
}
