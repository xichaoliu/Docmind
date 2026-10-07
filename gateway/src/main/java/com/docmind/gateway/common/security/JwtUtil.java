package com.docmind.gateway.common.security;

import com.docmind.gateway.config.property.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    @Autowired
    private JwtProperties jwtProperties;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
    }

    public String generateToken(String userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() +  jwtProperties.getExpirationHours() * 3600_000);
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    /**
     * 解析失败（过期 / 签名不对 / 格式错）统一抛 JwtException，
     * 由调用方决定怎么处理，这里不吞异常也不转成业务异常。
     */
    public String parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
}
