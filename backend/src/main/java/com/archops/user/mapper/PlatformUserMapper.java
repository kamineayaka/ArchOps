package com.archops.user.mapper;

import com.archops.user.domain.PlatformUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlatformUserMapper extends BaseMapper<PlatformUser> {
}
