package com.archops.graph.service;

import com.archops.common.exception.BusinessException;
import com.archops.graph.domain.GraphMeta;
import com.archops.graph.repository.GraphMetaRepository;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraphVersionService {

    private final GraphMetaRepository graphMetaRepository;

    public GraphVersionService(GraphMetaRepository graphMetaRepository) {
        this.graphMetaRepository = graphMetaRepository;
    }

    @Transactional(readOnly = true)
    public long currentVersion() {
        return graphMetaRepository.findById(GraphMeta.GLOBAL_KEY)
                .map(GraphMeta::getGraphVersion)
                .orElse(0L);
    }

    @Transactional
    public GraphMeta lockGlobal() {
        return graphMetaRepository.findByKeyForUpdate(GraphMeta.GLOBAL_KEY)
                .orElseGet(() -> {
                    GraphMeta meta = new GraphMeta();
                    meta.setKey(GraphMeta.GLOBAL_KEY);
                    meta.setGraphVersion(0);
                    meta.setUpdatedAt(Instant.now());
                    return graphMetaRepository.save(meta);
                });
    }

    @Transactional
    public long bump(GraphMeta meta, String bookmark) {
        if (meta == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "GRAPH_META_MISSING", "graph_meta 缺失");
        }
        long next = meta.getGraphVersion() + 1;
        meta.setGraphVersion(next);
        meta.setNeo4jBookmark(bookmark);
        meta.setUpdatedAt(Instant.now());
        graphMetaRepository.save(meta);
        return next;
    }
}
