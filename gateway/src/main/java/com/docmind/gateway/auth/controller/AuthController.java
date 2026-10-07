package com.docmind.gateway.auth.controller;

import com.docmind.gateway.auth.dto.LoginRequest;
import com.docmind.gateway.auth.dto.AuthResponse;
import com.docmind.gateway.auth.dto.RegisterRequest;
import com.docmind.gateway.auth.entity.SysUser;
import com.docmind.gateway.auth.service.AuthService;
import com.docmind.gateway.common.exception.BizException;
import com.docmind.gateway.common.result.Result;
import com.docmind.gateway.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /** 前端刷新页面后用它拿当前用户，顺便验证 token 是否还有效 */
    @GetMapping("/me")
    public Result<SysUser> me() {
        SysUser user = authService.getById(CurrentUser.require());
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        return Result.success(user);
    }
}
