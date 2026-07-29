package com.archops.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void okWrapsData() {
        ApiResponse<String> response = ApiResponse.ok("payload");
        assertTrue(response.success());
        assertEquals("OK", response.code());
        assertEquals("success", response.message());
        assertEquals("payload", response.data());
    }

    @Test
    void okWithCustomMessage() {
        ApiResponse<Void> response = ApiResponse.ok("已退出登录", null);
        assertTrue(response.success());
        assertEquals("已退出登录", response.message());
        assertNull(response.data());
    }

    @Test
    void errorClearsData() {
        ApiResponse<Object> response = ApiResponse.error("AUTH_FAILED", "用户名或密码错误");
        assertFalse(response.success());
        assertEquals("AUTH_FAILED", response.code());
        assertEquals("用户名或密码错误", response.message());
        assertNull(response.data());
    }
}
