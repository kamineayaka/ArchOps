package com.archops.graph.repository;

import com.archops.graph.domain.GraphMeta;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface GraphMetaRepository extends JpaRepository<GraphMeta, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from GraphMeta m where m.key = :key")
    Optional<GraphMeta> findByKeyForUpdate(String key);
}
