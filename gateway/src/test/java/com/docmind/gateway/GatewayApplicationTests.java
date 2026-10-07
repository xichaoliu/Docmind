package com.docmind.gateway;

import com.docmind.gateway.common.exception.BizException;
import com.docmind.gateway.common.security.JwtUtil;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.slf4j.LoggerFactory;

@Slf4j
@SpringBootTest
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class GatewayApplicationTests {
    @Autowired
    private final JwtUtil jwtUtil;

    @Test
    void 签发再解析_能拿回原来的userId() {
        String token = jwtUtil.generateToken("user-123", "alice");
        System.out.println("拿到原始用户id  "+ jwtUtil.parseUserId(token));
        assertThat(jwtUtil.parseUserId(token)).isEqualTo("user-123");
    }

    @Test
    void parseUserId_shouldThrow_forTamperedToken() {
        // JWT 过期/篡改 token 应该被拒绝
        String token = jwtUtil.generateToken("user-123", "admin");
        String tampered = token.substring(0, token.length() - 5) + "xxxxx";  // 篡改签名部分

        assertThrows(JwtException.class, () -> jwtUtil.parseUserId(tampered));
    }

}
