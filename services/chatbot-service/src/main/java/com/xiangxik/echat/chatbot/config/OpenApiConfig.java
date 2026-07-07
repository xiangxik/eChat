package com.xiangxik.echat.chatbot.config;

import com.xiangxik.echat.chatbot.api.admin.AdminAuthController;
import com.xiangxik.echat.chatbot.api.admin.AdminTokenInterceptor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class OpenApiConfig {

    private static final String ADMIN_TOKEN_SCHEME = "AdminToken";
    private static final String ADMIN_SESSION_COOKIE_SCHEME = "AdminSessionCookie";

    @Bean
    OpenAPI echatOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("eChat Chatbot Service API")
                        .version("0.1.0")
                        .description("Enterprise chatbot runtime and administration API."))
                .components(new Components()
                        .addSecuritySchemes(ADMIN_TOKEN_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(AdminTokenInterceptor.ADMIN_TOKEN_HEADER)
                                .description("Admin API token. Use the configured ADMIN_TOKEN value."))
                        .addSecuritySchemes(ADMIN_SESSION_COOKIE_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(AdminAuthController.ADMIN_SESSION_COOKIE)
                                .description("HTTP-only admin session cookie issued by /api/admin/auth/login.")));
    }

    @Bean
    OperationCustomizer adminSecurityOperationCustomizer() {
        return (operation, handlerMethod) -> {
            if (requiresAdminSecurity(handlerMethod)) {
                operation.addSecurityItem(new SecurityRequirement().addList(ADMIN_TOKEN_SCHEME));
                operation.addSecurityItem(new SecurityRequirement().addList(ADMIN_SESSION_COOKIE_SCHEME));
                operation.addParametersItem(new Parameter()
                        .in("header")
                        .name("X-Tenant-Id")
                        .required(false)
                        .schema(new StringSchema())
                        .description("Optional tenant scope for ABAC checks. Non-super-admin principals must use their configured tenant."));
            }
            return operation;
        };
    }

    private boolean requiresAdminSecurity(HandlerMethod handlerMethod) {
        Package controllerPackage = handlerMethod.getBeanType().getPackage();
        if (controllerPackage == null || !controllerPackage.getName().endsWith(".api.admin")) {
            return false;
        }
        return !isPublicAdminAuthMethod(handlerMethod);
    }

    private boolean isPublicAdminAuthMethod(HandlerMethod handlerMethod) {
        if (!AdminAuthController.class.equals(handlerMethod.getBeanType())) {
            return false;
        }
        String methodName = handlerMethod.getMethod().getName();
        return "login".equals(methodName) || "logout".equals(methodName);
    }
}