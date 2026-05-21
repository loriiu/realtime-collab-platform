package com.collab.platform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collab.platform.common.core.exception.BizException;
import com.collab.platform.common.core.result.ResultCode;
import com.collab.platform.common.redis.util.RedisUtil;
import com.collab.platform.common.security.util.JwtUtil;
import com.collab.platform.user.entity.User;
import com.collab.platform.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * User business logic: register, login, info.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String TOKEN_KEY_PREFIX = "token:";
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    public UserService(UserMapper userMapper, JwtUtil jwtUtil, RedisUtil redisUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
    }

    /**
     * Register a new user.
     *
     * @param user entity with raw password (will be encoded)
     * @throws BizException if the username already exists
     */
    public void register(User user) {
        // Check duplicate username
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, user.getUsername()));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "用户名已存在");
        }

        // Encode password
        user.setPassword(ENCODER.encode(user.getPassword()));

        // Set defaults
        if (user.getNickname() == null || user.getNickname().isBlank()) {
            user.setNickname(user.getUsername());
        }
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());

        userMapper.insert(user);
        log.info("User registered: id={}, username={}", user.getId(), user.getUsername());
    }

    /**
     * Authenticate user credentials and return a JWT token.
     *
     * @param username login username
     * @param password raw password
     * @return JWT token string (without "Bearer " prefix)
     * @throws BizException if credentials are invalid
     */
    public String login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        if (!ENCODER.matches(password, user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "账号已被禁用");
        }

        // Issue token
        String token = jwtUtil.createToken(user.getId());

        // Persist token in Redis with the same TTL
        long ttl = 7200L;
        redisUtil.set(TOKEN_KEY_PREFIX + user.getId(), token, ttl, TimeUnit.SECONDS);

        log.info("User logged in: id={}, username={}", user.getId(), user.getUsername());
        return token;
    }

    /**
     * Retrieve user profile by ID (password excluded).
     *
     * @param userId the user ID (from JWT)
     * @return user entity with password cleared
     * @throws BizException if user not found
     */
    public User info(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "用户不存在");
        }
        // Never expose password
        user.setPassword(null);
        return user;
    }

    /**
     * Retrieve user profile by ID, returning null if not found (no exception).
     * Used by batch queries where missing users should be silently skipped.
     */
    public User infoOrNull(Long userId) {
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }
}
