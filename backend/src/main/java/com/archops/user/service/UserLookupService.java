package com.archops.user.service;

import com.archops.user.domain.PlatformUser;
import com.archops.user.mapper.PlatformUserMapper;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserLookupService {

    private final PlatformUserMapper platformUserMapper;

    public UserLookupService(PlatformUserMapper platformUserMapper) {
        this.platformUserMapper = platformUserMapper;
    }

    public Optional<PlatformUser> findById(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(platformUserMapper.selectById(userId.trim()));
    }
}
