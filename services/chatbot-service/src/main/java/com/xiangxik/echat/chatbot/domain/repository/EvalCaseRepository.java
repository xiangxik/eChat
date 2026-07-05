package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.EvalCase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalCaseRepository extends JpaRepository<EvalCase, Long> {

    List<EvalCase> findByDatasetIdOrderByIdAsc(Long datasetId);
}
