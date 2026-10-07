package com.docmind.gateway.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.docmind.gateway.auth.dto.AuthResponse;
import com.docmind.gateway.auth.dto.LoginRequest;
import com.docmind.gateway.auth.dto.RegisterRequest;
import com.docmind.gateway.auth.entity.SysUser;

public interface  AuthService extends IService<SysUser> {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
