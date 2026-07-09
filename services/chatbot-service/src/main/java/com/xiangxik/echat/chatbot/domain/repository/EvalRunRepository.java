package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.EvalRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalRunRepository extends JpaRepository<EvalRun, Long> {

	Optional<EvalRun> findByTenantIdAndId(String tenantId, Long id);
}
