package com.xiangxik.echat.chatbot.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthenticationFactory {

    public Authentication create(AdminPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, "N/A",
                principal.roles().stream()
                        .map(role -> "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .toList());
    }
}