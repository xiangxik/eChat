package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.ContextPolicy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContextPolicyRepository extends JpaRepository<ContextPolicy, Long> {

    Optional<ContextPolicy> findByName(String name);

    List<ContextPolicy> findByEnabledTrueOrderByNameAsc();
}