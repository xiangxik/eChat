package com.xiangxik.echat.chatbot.api.dto;

import java.util.Set;

public record AdminSessionResponse(
		boolean authenticated,
		String actorId,
		String displayName,
		String tenantId,
		Set<String> roles,
		boolean superAdmin
) {

	public static AdminSessionResponse authenticated(String actorId, String displayName, String tenantId,
													 Set<String> roles) {
		Set<String> normalizedRoles = roles == null ? Set.of() : Set.copyOf(roles);
		return new AdminSessionResponse(true, actorId, displayName, tenantId, normalizedRoles,
				normalizedRoles.contains("SUPER_ADMIN"));
	}

	public static AdminSessionResponse basicAuthenticated() {
		return new AdminSessionResponse(true, null, null, null, Set.of(), false);
	}
}