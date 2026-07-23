package com.archops.knowledge.architecture.repository;

import com.archops.knowledge.architecture.domain.ToolExecutionEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolExecutionEventRepository extends JpaRepository<ToolExecutionEvent, Long> {
    List<ToolExecutionEvent> findByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
