package com.collab.platform.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collab.platform.message.entity.Session;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for the sessions table.
 */
@Mapper
public interface SessionMapper extends BaseMapper<Session> {
}
