package com.archops.graph.repository;

import com.archops.graph.domain.TerminalSessionDock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalSessionDockRepository extends JpaRepository<TerminalSessionDock, Long> {
    List<TerminalSessionDock> findByUserIdOrderByPinnedDescLastOpenedAtDesc(Long userId);

    Optional<TerminalSessionDock> findByUserIdAndElementId(Long userId, UUID elementId);

    void deleteByUserIdAndElementId(Long userId, UUID elementId);
}
