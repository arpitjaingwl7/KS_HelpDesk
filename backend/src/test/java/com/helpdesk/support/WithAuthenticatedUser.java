package com.helpdesk.support;

import com.helpdesk.security.Role;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

/** Runs a web slice test as an {@code AuthenticatedUser} with the given role (no JWT needed). */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithAuthenticatedUserFactory.class)
public @interface WithAuthenticatedUser {

    Role role() default Role.AGENT;

    String userId() default "00000000-0000-7000-8000-000000000001";

    String organizationId() default "00000000-0000-7000-8000-0000000000aa";
}
