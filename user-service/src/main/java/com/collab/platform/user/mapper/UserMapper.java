package com.collab.platform.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collab.platform.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for the {@code users} table.
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
