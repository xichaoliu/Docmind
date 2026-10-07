# Gateway 鉴权与用户管理模块设计（轻量版）

> 适用模块：`gateway/`（Spring Boot 3.5.6 + Java 21 + MyBatis-Plus 3.5.9 + PostgreSQL）
> 关联前端：`web/`（React 19 + Vite + zustand + antd）
> 决策：**不用 Spring Security**，用「拦截器 + ThreadLocal + jjwt」；Token 采用 **Bearer + localStorage**
> 全部后端代码约 **360 行**，新增 12 个文件

---

## 0. 一句话方案

```
请求 → AuthInterceptor.preHandle() 解析 token → userId 放进 UserContext(ThreadLocal) → Controller 里 UserContext.require() 取出来用
```

就这三步。**没有过滤器链，没有 SecurityContext，没有 EntryPoint，CORS 配置一行都不用改。**

---

## 1. 为什么不用 Spring Security（先定调）

这是面试必被问的一题，方案选型本身就是考点。

| | Spring Security 版 | 本方案（拦截器版） |
| --- | --- | --- |
| 新增文件 | 约 15 个 | 12 个（多数是 DTO 和实体） |
| 核心代码量 | 600+ 行 | **360 行** |
| 要理解的概念 | 过滤器链、`SecurityContextHolder` 传播策略、`AuthenticationEntryPoint`、`AccessDeniedHandler`、`CorsConfigurationSource`、`OncePerRequestFilter` | Interceptor、ThreadLocal |
| 401 怎么返回 | 必须手写 EntryPoint + AccessDeniedHandler | **抛 `BizException(401)` 就行**，已有 `GlobalExceptionHandler` 自动接管 |
| CORS | 必须重写，否则预检 OPTIONS 被拦成 401 | **`CorsConfig.java` 原样不动** |
| 认证放哪 | 散在 filter / config / entrypoint / handler 五个文件 | 集中在 `AuthInterceptor` 一个方法 |

**这个取舍怎么答（重要）：**

> 这个项目只需要「有没有登录」这一层。Spring Security 的过滤器链、方法级权限、CSRF、OAuth2 这些能力我用不上，但要为此写 200 多行配置，而且它和我的 CORS 配置会打架，得再改一遍。用拦截器 60 行就够，认证逻辑集中在一个方法里，好读也好改。**如果将来要做 OAuth2 登录或者细粒度权限，我会换成 Spring Security，因为那时候它的生态才是净收益。**

最后这句是关键：既说了取舍，又表明你知道它的边界在哪 —— 这比硬上框架然后被问穿要加分得多。

---

## 2. 请求全景

```
┌──────────┐   Authorization: Bearer xxx.yyy.zzz
│ 浏览器   │ ────────────────────────────────────┐
└──────────┘                                     │
                                                 ▼
                                    ┌────────────────────────────┐
                                    │ CorsConfig (MVC, 原样不动) │
                                    └────────────┬───────────────┘
                                                 ▼
                                    ┌────────────────────────────┐
                                    │ AuthInterceptor.preHandle  │
                                    │  1. OPTIONS? → 放行        │
                                    │  2. 没头 / 不是 Bearer?    │
                                    │     → throw BizException401│
                                    │  3. jwtUtil.parseUserId()  │
                                    │  4. UserContext.set(id)    │
                                    └────────────┬───────────────┘
                                                 ▼
                                    ┌────────────────────────────┐
                                    │ Controller                 │
                                    │ ConversationController ... │
                                    └────────────┬───────────────┘
                                                 ▼
                                    ┌────────────────────────────┐
                                    │ Service                    │
                                    │ UserContext.require()      │
                                    │   → 拿到当前 userId        │
                                    └────────────┬───────────────┘
                                                 ▼
                              ┌──────────────────────────────────────┐
                              │ 抛出的 BizException(401)             │
                              │  ↓                                   │
                              │ GlobalExceptionHandler               │
                              │  → HTTP 401 + {code:401,message:...} │
                              └──────────────────────────────────────┘
```

**一个必须理解的点**：拦截器里抛的异常，为什么能被 `@RestControllerAdvice` 接住？

因为 `HandlerInterceptor.preHandle()` 是在 `DispatcherServlet.doDispatch()` 的 **try 块内部**调用的，所以它抛的异常和 Controller 里抛的异常走的是同一条路 —— `processDispatchResult` → `processHandlerException` → `@ExceptionHandler`。

这就是为什么**不需要**写 `AuthenticationEntryPoint`：那些类存在的意义是处理 **Filter 层**的异常，而 Filter 在 DispatcherServlet 外面，异常到不了 `@RestControllerAdvice`。我们没用 Filter，所以这一整套都不需要。

---

## 3. 核心三件套（完整代码，共 147 行）

### 3.1 `common/security/JwtUtil.java`

只做两件事：把 userId 签成 token，把 token 解回 userId。

```java
package com.docmind.gateway.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expire-minutes:120}") long expireMinutes) {
        // secret 不足 32 字节时 Keys.hmacShaKeyFor 直接抛异常 —— 启动即失败，比线上才发现好
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireMinutes * 60_000L;
    }

    public String generate(String userId, String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析失败（过期 / 签名不对 / 格式错）统一抛 JwtException，
     * 由调用方决定怎么处理，这里不吞异常也不转成业务异常。
     */
    public String parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
}
```

### 3.2 `common/security/UserContext.java`

```java
package com.docmind.gateway.common.security;

import com.docmind.gateway.common.exception.BizException;

public final class UserContext {

    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(String userId) {
        CURRENT_USER_ID.set(userId);
    }

    /** 拿不到就抛 401，业务代码里不用再判空 */
    public static String require() {
        String userId = CURRENT_USER_ID.get();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }

    /** 必须清理：Tomcat 线程是复用的，不清理会让下一个请求读到上一个用户 */
    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
```

### 3.3 `common/security/AuthInterceptor.java`

整个鉴权逻辑就在这里，一个方法。

```java
package com.docmind.gateway.common.security;

import com.docmind.gateway.common.exception.BizException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements AsyncHandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 跨域预检请求不带 Authorization，必须放行，否则前端所有请求都卡在预检上
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BizException(401, "未登录");
        }

        try {
            UserContext.set(jwtUtil.parseUserId(header.substring(BEARER_PREFIX.length())));
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(401, "登录已过期，请重新登录");
        }
        return true;
    }

    /** 普通请求走完就清理 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * SSE 流式接口是异步请求：方法体返回 Flux 后 Servlet 线程立刻归还线程池，
     * 这时 afterCompletion 还没执行，所以要在并发处理开始时先清一次。
     */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request,
                                               HttpServletResponse response, Object handler) {
        UserContext.clear();
    }
}
```

---

## 4. 接入点（4 处改动）

### 4.1 `pom.xml` —— 注意**不要**引 `spring-boot-starter-security`

```xml
<!-- JWT 签发与解析 -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.7</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.7</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.7</version>
    <scope>runtime</scope>
</dependency>

<!-- 只要它的 BCrypt，不要 starter -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>

<!-- @Valid 参数校验 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

> `spring-security-crypto` 是个独立小 jar，**不会**触发 Boot 的 Security 自动装配（那需要 `spring-security-config`/`core`），所以引它不会给你带来默认登录页和过滤器链。版本由 Boot parent 管理，不用写。
>
> `jjwt-impl` / `jjwt-jackson` 必须是 `runtime` 作用域，否则运行时会报 `ClassNotFoundException: DefaultJwtBuilder`。

### 4.2 `config/WebMvcConfig.java`（新建）

白名单在这里一眼可见。

```java
package com.docmind.gateway.config;

import com.docmind.gateway.common.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register"
                );
    }
}
```

> `CorsConfig.java` **保持原样**，和本方案不冲突。这正是比 Spring Security 省心的地方。

### 4.3 `GatewayApplication.java` —— `@MapperScan` 必须加新包

```java
// 改前
@MapperScan("com.docmind.gateway.system.mapper")
// 改后
@MapperScan({"com.docmind.gateway.system.mapper", "com.docmind.gateway.auth.mapper"})
```

> 漏了这行，启动直接报 `SysUserMapper` bean 找不到。

### 4.4 `GlobalExceptionHandler.java` —— 加两个 handler

```java
@ExceptionHandler(BizException.class)
public Result<Void> handleBiz(BizException e, HttpServletResponse response) {
    response.setStatus(e.getCode());          // 真实返回 401/403/404，便于前端和监控
    return Result.error(e.getCode(), e.getMessage());
}

@ExceptionHandler(MethodArgumentNotValidException.class)
public Result<Void> handleValidation(MethodArgumentNotValidException e, HttpServletResponse response) {
    String message = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getDefaultMessage() == null ? f.getField() : f.getDefaultMessage())
            .collect(Collectors.joining("; "));
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    return Result.error(400, message);
}
```

配套的 `common/exception/BizException.java`：

```java
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

---

## 5. 用户模块

### 5.1 建表

追加到 `gateway/src/main/resources/sql/schema.sql`：

```sql
DROP TABLE IF EXISTS sys_user CASCADE;

CREATE TABLE sys_user (
    id            VARCHAR(32)  PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,           -- BCrypt 哈希，固定 60 字符
    nickname      VARCHAR(64),
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);
```

给已有的两张业务表加归属列（新库由 `schema.sql` 直接建好，**已有库必须单独执行**，因为 `schema.sql` 只在 postgres 数据卷为空时跑一次）：

```sql
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS user_id VARCHAR(32) REFERENCES sys_user(id) ON DELETE CASCADE;
ALTER TABLE document     ADD COLUMN IF NOT EXISTS user_id VARCHAR(32) REFERENCES sys_user(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_conversation_user_id ON conversation(user_id);
CREATE INDEX IF NOT EXISTS idx_document_user_id     ON document(user_id);
```

主键沿用项目约定：MyBatis-Plus `IdType.ASSIGN_UUID` 生成 32 位无横线 UUID，正好匹配 `VARCHAR(32)`。

### 5.2 `auth/entity/SysUser.java`

```java
@Getter
@Setter
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String username;

    /**
     * BCrypt 哈希值。加 @JsonIgnore 后这个实体可以直接当接口返回值，
     * 不用再单独写一个 UserVO，密码也绝不会被序列化出去。
     */
    @JsonIgnore
    private String passwordHash;

    private String nickname;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

### 5.3 `auth/mapper/SysUserMapper.java`

```java
public interface SysUserMapper extends BaseMapper<SysUser> {
}
```

> 基础增删改查全由 `BaseMapper` 提供，一个方法都不用写。

### 5.4 `auth/service/AuthService.java`

**故意不拆 interface + impl** —— 只有一个实现，拆了只是多两个文件。

```java
@Service
@RequiredArgsConstructor
public class AuthService extends ServiceImpl<SysUserMapper, SysUser> {

    private final JwtUtil jwtUtil;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 注册成功直接签发 token，前端不用再调一次登录 */
    public String register(RegisterRequest request) {
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
        return jwtUtil.generate(user.getId(), user.getUsername());
    }

    public String login(LoginRequest request) {
        SysUser user = this.getOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.username()));

        // 故意不区分「用户不存在」和「密码错误」：否则可以靠错误信息枚举出哪些用户名已注册
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }

        return jwtUtil.generate(user.getId(), user.getUsername());
    }
}
```

### 5.5 `auth/controller/AuthController.java`

只有 3 个接口。

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(new LoginResponse(authService.register(request)));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(new LoginResponse(authService.login(request)));
    }

    /** 前端刷新页面后用它拿当前用户，顺便验证 token 是否还有效 */
    @GetMapping("/me")
    public Result<SysUser> me() {
        SysUser user = authService.getById(UserContext.require());
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        return Result.success(user);
    }
}
```

DTO 用 record，放在 `auth/dto/`：

```java
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度需在 3-32 之间")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度至少 8 位")
        String password
) {
}

public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password
) {
}

public record LoginResponse(String token) {
}
```

---

## 6. 配置

`application-dev.yml`：

```yaml
app:
  upload-dir: ${UPLOAD_DIR:./uploads}
  jwt:
    # HS256 要求 secret 至少 32 字节，否则应用启动就会失败
    secret: ${JWT_SECRET:docmind-dev-secret-please-change-me-32bytes-min}
    expire-minutes: ${JWT_EXPIRE_MINUTES:120}
```

`application-prod.yml` 去掉默认值，强制注入：

```yaml
  jwt:
    secret: ${JWT_SECRET}
    expire-minutes: ${JWT_EXPIRE_MINUTES:120}
```

`docker-compose.yml` 的 gateway 服务补 `- JWT_SECRET=${JWT_SECRET:?JWT_SECRET must be set}`。

---

## 7. 前端改动（5 处）

### 7.1 `web/src/store/authStore.ts`（新建）

```ts
import { create } from "zustand";
import { persist } from "zustand/middleware";

export interface AuthUser {
  id: string;
  username: string;
  nickname?: string;
}

interface AuthState {
  token: string | null;
  user: AuthUser | null;
  setAuth: (token: string, user: AuthUser) => void;
  logout: () => void;
}

/** 登录态存 localStorage；组件外也能读：useAuthStore.getState().token */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setAuth: (token, user) => set({ token, user }),
      logout: () => set({ token: null, user: null }),
    }),
    { name: "docmind-auth" }
  )
);
```

### 7.2 `web/src/utils/client.ts`

```ts
import { useAuthStore } from "../store/authStore";

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = useAuthStore.getState().token;

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,                        // ① options 必须先展开
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,             // ② 再让调用方覆盖
    },
  });

  if (response.status === 401) {
    useAuthStore.getState().logout();
    if (!window.location.pathname.startsWith("/login")) {
      window.location.href = "/login";
    }
    throw new ApiError(401, "登录已过期，请重新登录");
  }

  // ...以下保持原有逻辑
}
```

> **顺手修一个隐患**：现有代码里 `...options` 展开在 `headers` **之后**，一旦有调用方传 `options.headers`，整个 headers 对象会被覆盖掉（包括 `Content-Type`）。现在没有调用方传所以没暴露，改造时必须调整顺序。
>
> 另外原来的 `console.log("response", response)` 建议删掉。

### 7.3 两处裸 `fetch` 必须同步加 header

**（1）`web/src/api/documents.ts` 的文件上传**

```ts
const token = useAuthStore.getState().token;

const res = await fetch(`${BASE_URL}/api/documents/upload`, {
  method: "POST",
  headers: token ? { Authorization: `Bearer ${token}` } : {},
  body: formData,   // 不要手动设 Content-Type，浏览器会自动加 multipart 边界
});
```

**（2）`web/src/hooks/useChatStream.ts` 的 SSE 流**

```ts
const response = await fetch(`${CHAT_STREAM_URL}/api/chat`, {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    ...(useAuthStore.getState().token
      ? { Authorization: `Bearer ${useAuthStore.getState().token}` }
      : {}),
  },
  body: JSON.stringify(data),
  signal: controller.signal,
});
```

> 这里用 `fetch` 而不是 `EventSource`，所以能带 header，不用把 token 塞进 query 参数（那样 token 会进 Nginx 日志）。如果哪天改成 `EventSource`，就必须改成 query 方案。

### 7.4 登录页 `web/src/pages/login/index.tsx`（新建）

```tsx
import { useState } from "react";
import { Button, Card, Form, Input, Tabs, message } from "antd";
import { useNavigate } from "react-router-dom";
import { login, register } from "../../api/auth";
import { useAuthStore } from "../../store/authStore";

export default function LoginPage() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: { username: string; password: string }, isRegister: boolean) => {
    setLoading(true);
    try {
      const token = isRegister ? await register(values) : await login(values);
      setAuth(token, { id: "", username: values.username });
      navigate("/chat");
    } catch (e) {
      message.error(e instanceof Error ? e.message : "操作失败");
    } finally {
      setLoading(false);
    }
  };

  const renderForm = (isRegister: boolean) => (
    <Form layout="vertical" onFinish={(v) => onFinish(v, isRegister)}>
      <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
        <Input autoComplete="username" />
      </Form.Item>
      <Form.Item
        name="password"
        label="密码"
        rules={[{ required: true }, ...(isRegister ? [{ min: 8, message: "至少 8 位" }] : [])]}
      >
        <Input.Password autoComplete={isRegister ? "new-password" : "current-password"} />
      </Form.Item>
      <Button type="primary" htmlType="submit" block loading={loading}>
        {isRegister ? "注册并登录" : "登录"}
      </Button>
    </Form>
  );

  return (
    <div style={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100vh" }}>
      <Card style={{ width: 380 }}>
        <Tabs
          items={[
            { key: "login", label: "登录", children: renderForm(false) },
            { key: "register", label: "注册", children: renderForm(true) },
          ]}
        />
      </Card>
    </div>
  );
}
```

### 7.5 `web/src/api/auth.ts`（新建）

```ts
import { apiClient } from "../utils/client";

export interface AuthUser {
  id: string;
  username: string;
  nickname?: string;
}

export const login = (body: { username: string; password: string }) =>
  apiClient.post<{ token: string }>("/api/auth/login", body).then((r) => r.token);

export const register = (body: { username: string; password: string }) =>
  apiClient.post<{ token: string }>("/api/auth/register", body).then((r) => r.token);

export const fetchMe = () => apiClient.get<AuthUser>("/api/auth/me");
```

### 7.6 路由与守卫

`web/src/router/index.tsx` —— 登录页放在 `MainLayout` **外面**（不需要侧边栏）：

```tsx
import LoginPage from "../pages/login";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  {
    path: "/",
    element: <MainLayout />,
    children: [ /* 原样保留 */ ],
  },
]);
```

`web/src/layouts/MainLayout.tsx` 顶部加守卫：

```tsx
const token = useAuthStore((s) => s.token);
if (!token) return <Navigate to="/login" replace />;
```

`web/src/pages/user/index.tsx`（现在是 `return null`）实现成个人页：

```tsx
export default function UserPage() {
  const { data: me } = useQuery({ queryKey: ["me"], queryFn: fetchMe });
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  return (
    <div style={{ padding: 24 }}>
      <Descriptions title="个人信息" column={1}>
        <Descriptions.Item label="用户名">{me?.username}</Descriptions.Item>
        <Descriptions.Item label="昵称">{me?.nickname ?? "-"}</Descriptions.Item>
      </Descriptions>
      <Button danger onClick={() => { logout(); navigate("/login"); }}>
        退出登录
      </Button>
    </div>
  );
}
```

---

## 8. 怎么验证（curl 三步）

```bash
# ① 未登录 → 必须 401，且 body 是 Result JSON
curl -i http://localhost:8080/api/conversations
# 期望：HTTP/1.1 401  {"code":401,"message":"未登录","data":null}

# ② 注册拿 token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"tester","password":"Test@12345"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
echo $TOKEN

# ③ 带 token → 200
curl -s http://localhost:8080/api/conversations -H "Authorization: Bearer $TOKEN"

# ④ 顺手验证参数校验
curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' -d '{"username":"ab","password":"123"}'
# 期望：HTTP 400  {"code":400,"message":"用户名长度需在 3-32 之间; 密码长度至少 8 位"}
```

**编译验证**：项目根目录 `gateway/` 下执行 `./mvnw clean compile`（本机若 wrapper 不可用，用系统 `mvn clean compile`）。

---

## 9. 面试问答准备

这一节是这个项目真正的价值所在 —— 代码能跑只是及格，能答上问题才是加分项。

**Q1：为什么用拦截器，不用 Spring Security？**
见 §1。核心答法：需求只有认证没有授权，Spring Security 的收益用不上、配置成本和概念负担却是实打实的；同时要说出「什么场景下我会换回它」。

**Q2：拦截器和过滤器（Filter）有什么区别？**
Filter 是 Servlet 规范，运行在 DispatcherServlet **之前**，拿不到 Controller 方法的信息；Interceptor 是 Spring MVC 的，能拿到 `HandlerMethod`，能用 Spring 容器里的 Bean，**抛出的异常能被 `@RestControllerAdvice` 捕获**。这也是本方案不用写 EntryPoint 的原因。

**Q3：ThreadLocal 为什么必须 clear？**
Tomcat 用线程池，线程会被复用。不 clear 的话，线程被下一个请求复用时还留着上一个用户的 userId，会造成**串号**（A 用户读到 B 的数据）。所以 `afterCompletion` 里必须 clear。

**Q4：那 SSE 流式接口呢？**
异步请求下，方法体返回 `Flux` 后 Servlet 线程立刻归还线程池，此时 `afterCompletion` 还没执行。所以额外实现 `AsyncHandlerInterceptor.afterConcurrentHandlingStarted`，在并发处理开始时先清一次。同时，**流回调（`doOnNext`/`doOnComplete`）跑在 reactor 线程，读不到 ThreadLocal**，必须在 Controller 方法体（请求线程）里先把 userId 取出来存成 final 变量。

**Q5：为什么不用 Session 而用 JWT？**
Session 需要服务端存储，多实例部署时要额外做共享（Redis 或粘性会话）；JWT 把状态放在客户端，服务端无状态，天然支持水平扩容。代价是**无法主动撤销**（见 §10）。

**Q6：密码为什么用 BCrypt，不用 MD5？**
MD5 快且无盐，彩虹表和暴力破解都挡不住。BCrypt 自带随机盐、计算代价可调，天生就是为存密码设计的慢哈希。另外 `matches()` 是拿原文和哈希比对，明文密码不落库也不落日志。

**Q7：为什么登录失败不区分「用户不存在」和「密码错误」？**
防止用户名枚举 —— 如果错误信息不同，攻击者可以靠它批量试出哪些用户名已注册。

**Q8：401 为什么还要设置 HTTP 状态码？**
统一响应体（`Result`）里已经有 `code` 了，但网关、监控、日志、前端的拦截器都是按 **HTTP 状态码**做判断的。只返回 HTTP 200 + body code=401，监控看不到异常、Nginx 也统计不到。所以 `handleBiz` 里 `response.setStatus(e.getCode())`。

**Q9：`@JsonIgnore` 起什么作用？**
`passwordHash` 加上它之后，`SysUser` 实体可以直接当接口返回值，不用担心密码哈希被序列化出去，也省掉了单独写一个 `UserVO` 的样板代码。

**Q10：用户 A 能不能拿到用户 B 的会话？**
目前**不能**，但需要自己做归属校验 —— 见 §11，这是下一步要做的，也是面试官最容易追问的点。

---

## 10. 已知取舍

| 取舍 | 代价 | 什么时候要解决 |
| --- | --- | --- |
| 无状态 JWT，不查库 | 禁用用户 / 改角色后，旧 token 在有效期内（默认 120 分钟）仍然可用；「退出登录」只是前端删了 localStorage | 上线前必须接受或解决 |
| 没有 refresh token | access token 一过期就得重新登录 | 体验要求高时加 |
| 没有角色 / 权限 | 所有登录用户权限相同 | 需要管理员功能时加 |
| 没有登录限流 | 可以被暴力破解 | 上线前建议加（按 IP + username 限流） |

---

## 11. 后续演进

### 11.1 数据隔离（建议紧接着做）

这是「用户管理」真正产生价值的地方，也是面试最容易被追问的：**加了登录之后，数据是不是真的隔离了？**

现在 `conversation` / `document` 表已经有 `user_id` 列，但业务查询还没用上。需要做的：

```java
// ConversationServiceImpl
@Override
public List<Conversation> listAll() {
    return this.list(Wrappers.<Conversation>lambdaQuery()
            .eq(Conversation::getUserId, UserContext.require())      // ← 关键
            .orderByDesc(Conversation::getUpdatedAt));
}
```

按 id 操作的接口必须校验归属，否则改个 URL 里的 id 就能读别人的数据：

```java
private Conversation getOwnedOrThrow(String conversationId) {
    Conversation c = getOne(Wrappers.<Conversation>lambdaQuery()
            .eq(Conversation::getId, conversationId)
            .eq(Conversation::getUserId, UserContext.require()));
    if (c == null) {
        throw new BizException(404, "会话不存在");   // 用 404 不用 403，避免泄露「该 id 存在但不属于你」
    }
    return c;
}
```

需要逐个改造的入口：

| 文件 | 方法 |
| --- | --- |
| `ConversationServiceImpl` | `listAll` / `create` / `updateTitle` / `deleteById` |
| `ConversationController` | `getOne` / `getMessages` |
| `DocumentServiceImpl` | 列表 / 详情 / 删除 |
| `ChatController` | `chat`（新建会话传 userId；已有会话先校验归属） |

> `ChatController` 注意：`create` 要传 userId，且**必须在方法体里先取** `UserContext.require()`，不能等到 `doOnComplete` 里再取（见 Q4）。

### 11.2 加刷新 token（轻量版怎么接）

本方案对这件事是友好的，基本是纯加法：

1. `JwtProperties` 加 `refresh-expire-days`
2. `JwtUtil` 拆成 `generateAccess()` / `generateRefresh()`
3. `LoginResponse` 加 `refreshToken`
4. 新增 `/api/auth/refresh`
5. 前端 401 时先尝试刷新再重放请求

**但有一件事必须做**：给 token 加 `typ` claim（`access` / `refresh`），并在 `AuthInterceptor` 里校验 `typ == "access"`。

原因：refresh token 也是用同一个密钥签出来的合法 JWT。如果不区分类型，**把 refresh token 当 access token 发给 `/api/conversations`，拦截器照样放行** —— 等于把有效期从 2 小时悄悄延长到 30 天，而且不会有任何报错，是个静默的安全漏洞。

前端那边要注意：**并发的多个 401 只能触发一次刷新**（用一个「刷新中的 Promise」去重），否则开了 token 轮换后会互相把对方挤下线；另外 **SSE 流不能自动重放**（已经吐了一部分内容，重放会重复），只能提示用户重试。

### 11.3 什么时候才需要 Redis

现在**不需要**。Redis 的价值不是「加速」而是**让撤销生效**：

- 管理员禁用用户 → 目前最多还能用 120 分钟
- 用户主动登出 → 目前旧 token 仍然有效

要解决就得在拦截器里查一次「这个 token 是否已被拉黑」，多一次 I/O。而 token 里目前**没有 `jti`**，没有唯一标识就无法拉黑。所以顺序是：

1. 签发时加 `.id(UUID.randomUUID().toString())`（**建议现在就加，成本几乎为零；一旦签发出去没带 jti 的 token，那批 token 就永远无法撤销**）
2. 黑名单存储：单实例用 **Caffeine 本地缓存**就够，零新增中间件
3. 等真的要多实例水平扩容了，再换成 Redis（撤销状态需要跨实例共享）

`docker-compose.yml` 目前是单实例 gateway，且 gateway / rag 全项目都没有 Redis，所以现在引 Redis 是纯新增运维组件，不划算。

---

## 12. 实施顺序

1. **建表 + 依赖**：`sys_user` 表、pom 依赖、`application-*.yml` 配 `app.jwt`
2. **核心三件套**：`JwtUtil` → `UserContext` → `AuthInterceptor` → `WebMvcConfig`
3. **改启动类 `@MapperScan`** + `GlobalExceptionHandler` 加 handler + `BizException`
4. **用户模块**：`SysUser` / `SysUserMapper` / DTO / `AuthService` / `AuthController`
5. **curl 验四步**（§8），确认 401 和 200 都对
6. **前端**：`authStore` → `api/auth.ts` → `client.ts` → 两处裸 fetch → 登录页 → 路由守卫
7. **数据隔离**（§11.1）—— 这一步做完，「用户管理」才算真的成立
