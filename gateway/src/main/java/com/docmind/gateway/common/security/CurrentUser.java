package com.docmind.gateway.common.security;

import com.docmind.gateway.common.exception.BizException;

public final class CurrentUser {

    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();

    private CurrentUser() {
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
