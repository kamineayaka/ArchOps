package com.archops.ai.provider.controller;

import com.archops.ai.provider.dto.AiModelInfo;
import com.archops.ai.provider.dto.AiProviderRequest;
import com.archops.ai.provider.dto.AiProviderResponse;
import com.archops.ai.provider.dto.ModelDefaultsResponse;
import com.archops.ai.provider.dto.PlatformAiSettingsRequest;
import com.archops.ai.provider.dto.PlatformAiSettingsResponse;
import com.archops.ai.provider.service.AiProviderService;
import com.archops.ai.provider.service.PlatformAiSettingsService;
import com.archops.common.dto.ApiResponse;
import com.archops.common.security.AuthUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiProviderController {

    private final AiProviderService providerService;
    private final PlatformAiSettingsService settingsService;

    public AiProviderController(AiProviderService providerService, PlatformAiSettingsService settingsService) {
        this.providerService = providerService;
        this.settingsService = settingsService;
    }

    @GetMapping("/providers")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<List<AiProviderResponse>> listProviders() {
        return ApiResponse.ok(providerService.listEnabled());
    }

    @GetMapping("/providers/all")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<List<AiProviderResponse>> listAllProviders() {
        return ApiResponse.ok(providerService.list());
    }

    @PostMapping("/providers")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<AiProviderResponse> create(
            @Valid @RequestBody AiProviderRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(providerService.create(request, principal.getUserId(), principal.getUsername()));
    }

    @PutMapping("/providers/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<AiProviderResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody AiProviderRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(providerService.update(id, request, principal.getUserId(), principal.getUsername()));
    }

    @DeleteMapping("/providers/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        providerService.delete(id, principal.getUserId(), principal.getUsername());
        return ApiResponse.ok(null);
    }

    @PostMapping("/providers/{id}/test")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<Map<String, String>> test(@PathVariable Long id) {
        return ApiResponse.ok(Map.of("status", providerService.testConnection(id)));
    }

    @GetMapping("/providers/{id}/models")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<List<AiModelInfo>> models(@PathVariable Long id) {
        return ApiResponse.ok(providerService.listModels(id));
    }

    /**
     * OpsKat GetModelDefaults analogue. Unknown models return zeros (not an error).
     */
    @GetMapping("/model-defaults")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_OPERATOR')")
    public ApiResponse<ModelDefaultsResponse> modelDefaults(@RequestParam("model") String model) {
        return ApiResponse.ok(providerService.modelDefaults(model));
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<PlatformAiSettingsResponse> getSettings() {
        return ApiResponse.ok(settingsService.toResponse());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<PlatformAiSettingsResponse> updateSettings(
            @Valid @RequestBody PlatformAiSettingsRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(settingsService.update(request, principal.getUserId(), principal.getUsername()));
    }
}
