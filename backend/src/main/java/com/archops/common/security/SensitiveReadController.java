package com.archops.common.security;

import com.archops.common.api.ApiResponse;
import com.archops.common.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stub classifier for sensitive business-data reads (ticket 06).
 * Rejection is immediate — not an approval gate.
 */
@RestController
@RequestMapping("/api/workbench")
@PreAuthorize("isAuthenticated()")
public class SensitiveReadController {

    @PostMapping("/sensitive-reads")
    public ApiResponse<Void> attemptSensitiveRead(@Valid @RequestBody SensitiveReadRequest request) {
        if (SensitiveBusinessReadClassifier.isSensitive(request.target(), request.intent())) {
            throw new BusinessException(
                    "SENSITIVE_BUSINESS_READ_DENIED",
                    "Sensitive business data read is refused (not approval-gated): "
                            + request.intent() + " @ " + request.target()
            );
        }
        return ApiResponse.ok(null);
    }

    public record SensitiveReadRequest(
            @NotBlank String target,
            @NotBlank String intent
    ) {
    }
}
