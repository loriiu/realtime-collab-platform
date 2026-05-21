package com.collab.platform.user.controller;

import com.collab.platform.common.core.exception.BizException;
import com.collab.platform.common.core.result.Result;
import com.collab.platform.common.core.result.ResultCode;
import com.collab.platform.user.dto.LoginDTO;
import com.collab.platform.user.dto.RegisterDTO;
import com.collab.platform.user.entity.User;
import com.collab.platform.user.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * User REST controller.
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * POST /user/register — create a new account.
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(dto.getPassword());
        user.setNickname(dto.getNickname());
        userService.register(user);
        return Result.success();
    }

    /**
     * POST /user/login — authenticate and return a JWT.
     */
    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody LoginDTO dto) {
        String token = userService.login(dto.getUsername(), dto.getPassword());
        return Result.success(token);
    }

    /**
     * GET /user/info — return the current user's profile.
     * The user ID is read from the {@code X-User-Id} header injected by the gateway.
     */
    @GetMapping("/info")
    public Result<User> info(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "Missing X-User-Id header");
        }
        Long userId;
        try {
            userId = Long.valueOf(userIdHeader);
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "Invalid X-User-Id header");
        }
        User user = userService.info(userId);
        return Result.success(user);
    }
}
