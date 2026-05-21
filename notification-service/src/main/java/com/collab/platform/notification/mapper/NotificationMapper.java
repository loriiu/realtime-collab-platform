package com.collab.platform.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collab.platform.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for the notifications table.
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
