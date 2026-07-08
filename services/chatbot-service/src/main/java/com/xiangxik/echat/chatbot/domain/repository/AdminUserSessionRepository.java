package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.AdminUserSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserSessionRepository extends JpaRepository<AdminUserSession, Long> {

    Optional<AdminUserSession> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);

    void deleteByTokenHash(String tokenHash);

    void deleteByExpiresAtBefore(Instant now);
}
