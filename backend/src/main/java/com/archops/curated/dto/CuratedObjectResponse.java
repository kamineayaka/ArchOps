package com.archops.curated.dto;

import com.archops.curated.CuratedObjectLabels;
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.domain.CuratedObjectKind;

import java.time.Instant;

public record CuratedObjectResponse(
        String id,
        CuratedObjectKind kind,
        String name,
        String objectId,
        String objectLabel,
        Instant createdAt
) {
    public static CuratedObjectResponse from(CuratedObject object) {
        String objectId = object.getImmutableObjectId();
        String objectLabel = objectId == null ? null : CuratedObjectLabels.formatObjectIdLabel(objectId);
        return new CuratedObjectResponse(
                object.getId(),
                object.getKind(),
                object.getName(),
                objectId,
                objectLabel,
                object.getCreatedAt()
        );
    }
}
