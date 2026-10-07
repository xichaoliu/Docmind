package com.docmind.gateway.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

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
