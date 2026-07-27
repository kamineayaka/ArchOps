package com.archops.graph.service;

import com.archops.asset.domain.Asset;
import com.archops.asset.repository.AssetRepository;
import com.archops.asset.repository.SshCredentialRepository;
import com.archops.common.exception.BusinessException;
import com.archops.graph.domain.TerminalSessionDock;
import com.archops.graph.dto.TerminalDockItem;
import com.archops.graph.dto.TerminalDockUpsertRequest;
import com.archops.graph.repository.TerminalSessionDockRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TerminalDockService {

    private final TerminalSessionDockRepository dockRepository;
    private final AssetRepository assetRepository;
    private final SshCredentialRepository sshCredentialRepository;

    public TerminalDockService(
            TerminalSessionDockRepository dockRepository,
            AssetRepository assetRepository,
            SshCredentialRepository sshCredentialRepository) {
        this.dockRepository = dockRepository;
        this.assetRepository = assetRepository;
        this.sshCredentialRepository = sshCredentialRepository;
    }

    @Transactional(readOnly = true)
    public List<TerminalDockItem> list(Long userId) {
        return dockRepository.findByUserIdOrderByPinnedDescLastOpenedAtDesc(userId).stream()
                .map(this::toItem)
                .filter(item -> item != null)
                .toList();
    }

    @Transactional
    public TerminalDockItem touch(Long userId, TerminalDockUpsertRequest request) {
        Asset asset = resolveAsset(request);
        TerminalSessionDock dock = dockRepository
                .findByUserIdAndElementId(userId, asset.getElementId())
                .orElseGet(TerminalSessionDock::new);
        dock.setUserId(userId);
        dock.setElementId(asset.getElementId());
        dock.setAssetId(asset.getId());
        if (request.pinned() != null) {
            dock.setPinned(request.pinned());
        }
        dock.setLastOpenedAt(Instant.now());
        dock = dockRepository.save(dock);
        return toItem(dock);
    }

    @Transactional
    public TerminalDockItem setPinned(Long userId, UUID elementId, boolean pinned) {
        TerminalSessionDock dock = dockRepository
                .findByUserIdAndElementId(userId, elementId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "DOCK_NOT_FOUND", "会话坞条目不存在"));
        dock.setPinned(pinned);
        dock = dockRepository.save(dock);
        return toItem(dock);
    }

    @Transactional
    public void remove(Long userId, UUID elementId) {
        dockRepository.deleteByUserIdAndElementId(userId, elementId);
    }

    private Asset resolveAsset(TerminalDockUpsertRequest request) {
        if (request.elementId() != null) {
            return assetRepository
                    .findByElementIdAndDeletedAtIsNull(request.elementId())
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "资产不存在"));
        }
        if (request.assetId() != null) {
            return assetRepository
                    .findByIdAndDeletedAtIsNull(request.assetId())
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "资产不存在"));
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "DOCK_REF_REQUIRED", "需要 elementId 或 assetId");
    }

    private TerminalDockItem toItem(TerminalSessionDock dock) {
        Asset asset = assetRepository.findByIdAndDeletedAtIsNull(dock.getAssetId()).orElse(null);
        if (asset == null) {
            return null;
        }
        boolean hasCred = sshCredentialRepository.findByAssetIdAndDeletedAtIsNull(asset.getId()).isPresent();
        return new TerminalDockItem(
                dock.getId(),
                asset.getElementId(),
                asset.getId(),
                asset.getName(),
                asset.getKind().name(),
                asset.getHost(),
                dock.isPinned(),
                hasCred,
                dock.getLastOpenedAt());
    }
}
