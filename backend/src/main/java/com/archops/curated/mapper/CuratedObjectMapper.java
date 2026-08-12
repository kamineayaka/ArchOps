package com.archops.curated.mapper;

import com.archops.curated.domain.CuratedObject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CuratedObjectMapper extends BaseMapper<CuratedObject> {
}
