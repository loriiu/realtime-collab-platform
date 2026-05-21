package com.collab.platform.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collab.platform.message.entity.Message;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for the messages table.
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
