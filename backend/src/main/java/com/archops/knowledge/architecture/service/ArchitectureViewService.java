package com.archops.knowledge.architecture.service;

import com.archops.knowledge.architecture.ArchitectureProperties;
import com.archops.knowledge.architecture.PartitionKeys;
import com.archops.knowledge.architecture.dto.ArchitectureViewResponse;
import com.archops.knowledge.architecture.dto.FactResponse;
import com.archops.knowledge.architecture.dto.PartitionDetailResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchitectureViewService {

    private final ArchitecturePartitionService partitionService;
    private final ArchitectureProperties properties;

    public ArchitectureViewService(
            ArchitecturePartitionService partitionService, ArchitectureProperties properties) {
        this.partitionService = partitionService;
        this.properties = properties;
    }

    @Transactional
    public ArchitectureViewResponse buildView(List<Long> targetAssetIds, List<Long> targetGroupIds) {
        partitionService.ensureGlobal();
        Set<String> keys = new LinkedHashSet<>();
        keys.add(PartitionKeys.GLOBAL);
        if (targetGroupIds != null) {
            for (Long groupId : targetGroupIds) {
                if (groupId != null && groupId > 0) {
                    keys.add(PartitionKeys.group(groupId));
                }
            }
        }
        if (targetAssetIds != null) {
            for (Long assetId : targetAssetIds) {
                if (assetId != null && assetId > 0) {
                    keys.add(PartitionKeys.asset(assetId));
                }
            }
        }

        List<PartitionDetailResponse> partitions = new ArrayList<>();
        StringBuilder md = new StringBuilder();
        for (String key : keys) {
            partitionService.getOrCreate(key);
            PartitionDetailResponse detail = partitionService.getDetail(key);
            partitions.add(detail);
            appendMarkdown(md, detail);
        }
        return new ArchitectureViewResponse(partitions, md.toString().trim());
    }

    /** Short prompt snippet: global summary + active facts (not the whole dump). */
    public String toPromptSnippet(ArchitectureViewResponse view) {
        if (view == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        PartitionDetailResponse global = view.partitions().stream()
                .filter(p -> PartitionKeys.GLOBAL.equals(p.partitionKey()))
                .findFirst()
                .orElse(null);
        if (global != null) {
            sb.append("## Global architecture\n");
            if (global.summary() != null && !global.summary().isBlank()) {
                sb.append(global.summary().trim()).append('\n');
            } else if (global.bodyMd() != null && !global.bodyMd().isBlank()) {
                String body = global.bodyMd().trim();
                int max = Math.min(body.length(), 500);
                sb.append(body, 0, max);
                if (body.length() > max) {
                    sb.append("…");
                }
                sb.append('\n');
            }
        }

        sb.append("\n## Active facts\n");
        for (PartitionDetailResponse p : view.partitions()) {
            if (p.facts() == null || p.facts().isEmpty()) {
                continue;
            }
            for (FactResponse f : p.facts()) {
                sb.append("- [")
                        .append(p.partitionKey())
                        .append("] ")
                        .append(f.factType())
                        .append(" ")
                        .append(f.subject())
                        .append(" ")
                        .append(f.predicate())
                        .append(" ")
                        .append(f.object());
                if (f.confidence() != null) {
                    sb.append(" (conf=").append(f.confidence()).append(')');
                }
                sb.append('\n');
            }
        }

        String result = sb.toString().trim();
        int maxChars = properties.getContextMaxChars();
        if (maxChars > 0 && result.length() > maxChars) {
            return result.substring(0, maxChars) + "…";
        }
        return result;
    }

    private static void appendMarkdown(StringBuilder md, PartitionDetailResponse detail) {
        md.append("## ").append(detail.partitionKey());
        if (detail.title() != null) {
            md.append(" — ").append(detail.title());
        }
        md.append(" (v").append(detail.latestVersion()).append(")\n");
        if (detail.summary() != null && !detail.summary().isBlank()) {
            md.append(detail.summary().trim()).append("\n\n");
        }
        if (detail.bodyMd() != null && !detail.bodyMd().isBlank()) {
            md.append(detail.bodyMd().trim()).append("\n\n");
        }
    }
}
