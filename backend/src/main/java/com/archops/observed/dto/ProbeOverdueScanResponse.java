package com.archops.observed.dto;

import java.util.List;

public record ProbeOverdueScanResponse(
        List<String> hollowedTaskIds
) {
}
