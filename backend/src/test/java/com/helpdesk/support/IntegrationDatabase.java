package com.helpdesk.support;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The database used by integration tests:
 * <ul>
 *   <li>by default a singleton Testcontainers {@code postgres:16-alpine}, shared by all IT classes;</li>
 *   <li>or, when {@code IT_DB_URL} is set (plus optional {@code IT_DB_USERNAME}/{@code IT_DB_PASSWORD}),
 *       an existing PostgreSQL 16 database — handy where Docker isn't available.</li>
 * </ul>
 * ITs are skipped (not failed) when neither is available.
 */
public final class IntegrationDatabase {

    private static PostgreSQLContainer<?> container;

    private IntegrationDatabase() {
    }

    /** Used by {@code @EnabledIf} on {@link AbstractIntegrationTest}. */
    public static boolean available() {
        return externalUrl() != null || dockerAvailable();
    }

    public static synchronized String url() {
        if (externalUrl() != null) {
            return externalUrl();
        }
        return container().getJdbcUrl();
    }

    public static String username() {
        return externalUrl() != null ? env("IT_DB_USERNAME", "helpdesk") : container().getUsername();
    }

    public static String password() {
        return externalUrl() != null ? env("IT_DB_PASSWORD", "helpdesk") : container().getPassword();
    }

    private static synchronized PostgreSQLContainer<?> container() {
        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("helpdesk").withUsername("helpdesk").withPassword("helpdesk");
            container.start();
        }
        return container;
    }

    private static String externalUrl() {
        String url = System.getenv("IT_DB_URL");
        return url == null || url.isBlank() ? null : url;
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
