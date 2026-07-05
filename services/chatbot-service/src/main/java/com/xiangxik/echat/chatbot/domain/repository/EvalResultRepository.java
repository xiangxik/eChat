package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.EvalResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalResultRepository extends JpaRepository<EvalResult, Long> {

    List<EvalResult> findByRunIdOrderByIdAsc(Long runId);
}
