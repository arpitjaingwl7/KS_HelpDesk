package com.helpdesk.config;

import com.helpdesk.common.error.ErrorCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Arrays;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc setup: API info, a global {@code bearerAuth} JWT scheme (Swagger UI "Authorize" button),
 * and an RFC 7807 {@code Problem} schema on every 4xx/5xx response.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    private static final String PROBLEM = "Problem";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    OpenAPI helpdeskOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Helpdesk API")
                        .version("v1")
                        .description("""
                                Multi-tenant helpdesk and ticketing API.

                                * Log in with `POST /api/v1/auth/login`, then click **Authorize** and paste the `accessToken`.
                                * The organization is always taken from the token — the API never accepts an organization id.
                                * Resources outside your visibility (another organization, another customer's ticket) return **404**, not 403.
                                * All errors are RFC 7807 Problem Details (`application/problem+json`) with a stable `code` field.
                                """)
                        .contact(new Contact().name("Helpdesk team"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url("/").description("This server")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSchemas(PROBLEM, problemSchema()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    /**
     * springdoc copies the success schema onto every documented response; replace it with the
     * Problem schema for errors, and document 500 everywhere.
     */
    @Bean
    OpenApiCustomizer problemResponsesCustomizer() {
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM, problemSchema());
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                operation.getResponses().forEach((status, response) -> {
                    if (status.startsWith("4") || status.startsWith("5")) {
                        response.setContent(problemContent());
                    }
                });
                operation.getResponses().putIfAbsent("500", new ApiResponse()
                        .description("INTERNAL_ERROR — unexpected error; details only in the server log")
                        .content(problemContent()));
            }));
        };
    }

    private static Content problemContent() {
        return new Content().addMediaType(PROBLEM_JSON,
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM)));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema<?> problemSchema() {
        Schema fieldError = new ObjectSchema()
                .addProperty("field", new StringSchema().example("subject"))
                .addProperty("message", new StringSchema().example("size must be between 3 and 200"))
                .addProperty("rejectedValue", new Schema<>().description("The rejected value (never echoed for passwords)"));
        StringSchema code = new StringSchema();
        code.setEnum(Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
        return new ObjectSchema()
                .description("RFC 7807 Problem Details, extended with code, traceId, timestamp and errors[]")
                .addProperty("type", new StringSchema().example("https://helpdesk.local/problems/validation-failed"))
                .addProperty("title", new StringSchema().example("Validation failed"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("detail", new StringSchema().example("Request has 1 invalid field"))
                .addProperty("instance", new StringSchema().example("/api/v1/tickets"))
                .addProperty("code", code.example("VALIDATION_FAILED"))
                .addProperty("traceId", new StringSchema().example("6f1c2a9e4b7d4c1a"))
                .addProperty("timestamp", new StringSchema().format("date-time"))
                .addProperty("errors", new ArraySchema().items(fieldError));
    }
}
