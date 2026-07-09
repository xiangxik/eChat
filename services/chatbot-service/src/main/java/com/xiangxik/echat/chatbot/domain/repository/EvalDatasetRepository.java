package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.EvalDataset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalDatasetRepository extends JpaRepository<EvalDataset, Long> {

    List<EvalDataset> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    java.util.Optional<EvalDataset> findByTenantIdAndId(String tenantId, Long id);
}
