package com.archops.curated.mapper;

import com.archops.curated.domain.CuratedFact;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CuratedFactMapper extends BaseMapper<CuratedFact> {
}
