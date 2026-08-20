package com.archops.curated.service;

import com.archops.common.exception.BusinessException;
import com.archops.curated.domain.CuratedFact;
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.domain.CuratedObjectKind;
import com.archops.curated.domain.CuratedRelationType;
import com.archops.curated.dto.ConfirmRunsOnRequest;
import com.archops.curated.dto.CreateContainerRequest;
import com.archops.curated.dto.CreateHostRequest;
import com.archops.curated.dto.CuratedObjectResponse;
import com.archops.curated.dto.CuratedRunsOnFactResponse;
import com.archops.curated.dto.ShouldWhereResponse;
import com.archops.curated.mapper.CuratedFactMapper;
import com.archops.curated.mapper.CuratedObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CuratedTruthService {

    private final CuratedObjectMapper curatedObjectMapper;
    private final CuratedFactMapper curatedFactMapper;

    public CuratedTruthService(CuratedObjectMapper curatedObjectMapper, CuratedFactMapper curatedFactMapper) {
        this.curatedObjectMapper = curatedObjectMapper;
        this.curatedFactMapper = curatedFactMapper;
    }

    @Transactional
    public CuratedObjectResponse createHost(CreateHostRequest request, String actorUserId) {
        String name = request.name().trim();
        CuratedObject host = new CuratedObject();
        host.setId(newId("host"));
        host.setKind(CuratedObjectKind.PHYSICAL_HOST);
        host.setName(name);
        host.setImmutableObjectId(null);
        host.setCreatedBy(actorUserId);
        host.setCreatedAt(Instant.now());
        curatedObjectMapper.insert(host);
        return CuratedObjectResponse.from(host);
    }

    @Transactional
    public CuratedObjectResponse createContainer(CreateContainerRequest request, String actorUserId) {
        String name = request.name().trim();
        String objectId = request.objectId().trim();
        Long dup = curatedObjectMapper.selectCount(new LambdaQueryWrapper<CuratedObject>()
                .eq(CuratedObject::getImmutableObjectId, objectId));
        if (dup != null && dup > 0) {
            throw new BusinessException("CURATED_OBJECT_ID_EXISTS",
                    "Container with archops.object_id already exists: " + objectId);
        }

        CuratedObject container = new CuratedObject();
        container.setId(newId("ctr"));
        container.setKind(CuratedObjectKind.DOCKER_CONTAINER);
        container.setName(name);
        container.setImmutableObjectId(objectId);
        container.setCreatedBy(actorUserId);
        container.setCreatedAt(Instant.now());
        curatedObjectMapper.insert(container);
        return CuratedObjectResponse.from(container);
    }

    @Transactional
    public CuratedRunsOnFactResponse confirmRunsOn(ConfirmRunsOnRequest request, String actorUserId) {
        CuratedObject container = requireObject(request.containerId().trim(), CuratedObjectKind.DOCKER_CONTAINER,
                "CURATED_CONTAINER_NOT_FOUND", "Docker container not found: ");
        CuratedObject host = requireObject(request.hostId().trim(), CuratedObjectKind.PHYSICAL_HOST,
                "CURATED_HOST_NOT_FOUND", "Physical host not found: ");

        if (findRunsOnFact(container.getId()) != null) {
            throw new BusinessException("CURATED_RUNS_ON_EXISTS",
                    "Curated 运行于 already exists for container: " + container.getId());
        }

        CuratedFact fact = new CuratedFact();
        fact.setId(newId("fact"));
        fact.setSubjectId(container.getId());
        fact.setRelationType(CuratedRelationType.RUNS_ON);
        fact.setTargetId(host.getId());
        fact.setCreatedBy(actorUserId);
        fact.setCreatedAt(Instant.now());
        curatedFactMapper.insert(fact);
        return toRunsOnResponse(fact, container, host);
    }

    @Transactional(readOnly = true)
    public CuratedRunsOnFactResponse getRunsOn(String containerId) {
        CuratedObject container = requireObject(containerId.trim(), CuratedObjectKind.DOCKER_CONTAINER,
                "CURATED_CONTAINER_NOT_FOUND", "Docker container not found: ");
        CuratedFact fact = requireRunsOnFact(container.getId());
        CuratedObject host = requireObject(fact.getTargetId(), CuratedObjectKind.PHYSICAL_HOST,
                "CURATED_HOST_NOT_FOUND", "Physical host not found: ");
        return toRunsOnResponse(fact, container, host);
    }

    /**
     * 规范问法：「应该在哪」→ curated track only.
     */
    @Transactional(readOnly = true)
    public ShouldWhereResponse shouldWhere(String containerId) {
        CuratedRunsOnFactResponse fact = getRunsOn(containerId);
        return new ShouldWhereResponse(
                "应该在哪",
                "CURATED",
                CuratedRelationType.RUNS_ON,
                CuratedRelationType.RUNS_ON.labelZh(),
                fact.subject(),
                new ShouldWhereResponse.CuratedHostValue(fact.target().id(), fact.target().name())
        );
    }

    private CuratedObject requireObject(String id, CuratedObjectKind expectedKind, String missingCode, String missingPrefix) {
        CuratedObject object = curatedObjectMapper.selectById(id);
        if (object == null) {
            throw new BusinessException(missingCode, missingPrefix + id);
        }
        if (object.getKind() != expectedKind) {
            throw new BusinessException("CURATED_OBJECT_KIND_MISMATCH",
                    "Object " + id + " is " + object.getKind() + ", expected " + expectedKind);
        }
        return object;
    }

    private CuratedFact findRunsOnFact(String containerId) {
        return curatedFactMapper.selectOne(new LambdaQueryWrapper<CuratedFact>()
                .eq(CuratedFact::getSubjectId, containerId)
                .eq(CuratedFact::getRelationType, CuratedRelationType.RUNS_ON));
    }

    private CuratedFact requireRunsOnFact(String containerId) {
        CuratedFact fact = findRunsOnFact(containerId);
        if (fact == null) {
            throw new BusinessException("CURATED_RUNS_ON_NOT_FOUND",
                    "No curated 运行于 fact for container: " + containerId);
        }
        return fact;
    }

    private static CuratedRunsOnFactResponse toRunsOnResponse(
            CuratedFact fact, CuratedObject container, CuratedObject host
    ) {
        return new CuratedRunsOnFactResponse(
                fact.getId(),
                CuratedRelationType.RUNS_ON,
                CuratedRelationType.RUNS_ON.labelZh(),
                CuratedObjectResponse.from(container),
                CuratedObjectResponse.from(host),
                fact.getCreatedAt()
        );
    }

    private static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
