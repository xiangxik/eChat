package com.xiangxik.echat.chatbot.security;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

public record AdminPrincipal(
        String actorId,
        String displayName,
        String tenantId,
        Set<String> roles,
        Map<String, Object> attributes
) implements Principal {

    public AdminPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    @Override
    public String getName() {
        return actorId;
    }
}