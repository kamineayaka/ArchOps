package com.archops.ai.dto;

import java.util.List;

public record ConversationTargetsRequest(List<Long> targetAssetIds, List<Long> targetGroupIds) {}
