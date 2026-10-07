package com.docmind.gateway.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.docmind.gateway.auth.dto.AuthResponse;
import com.docmind.gateway.auth.dto.LoginRequest;
import com.docmind.gateway.auth.dto.RegisterRequest;
import com.docmind.gateway.auth.entity.SysUser;
import com.docmind.gateway.auth.mapper.SysUserMapper;
import com.docmind.gateway.auth.service.AuthService;
import com.docmind.gateway.common.exception.BizException;
import com.docmind.gateway.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements AuthService {
    private final JwtUtil jwtUtil;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 注册成功签发 token，前端不用再调一次登录 */
    public AuthResponse register(RegisterRequest request) {
        boolean taken = this.exists(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.username()));
        if (taken) {
            throw new BizException(400, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.username());
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        this.save(user);

        return buildResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        SysUser user = this.getOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.username()));

        // 不区分「用户不存在」和「密码错误」：否则可以靠错误信息枚举出哪些用户名已注册
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }

        return buildResponse(user);
    }

    private AuthResponse buildResponse(SysUser user) {
        AuthResponse resp = new AuthResponse();
        resp.setToken(jwtUtil.generateToken(user.getId(), user.getUsername()));
        resp.setUsername(user.getUsername());
        return resp;
    }
}
