package com.docmind.gateway.system.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.docmind.gateway.system.handler.PostgresJsonbTypeHandler;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.JdbcType;

/**
 * <p>
 * 
 * </p>
 *
 * @author xichaoliu
 * @since 2026-09-24
 */
@Getter
@Setter
@TableName(autoResultMap = true)
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private Object conversationId;

    private String role;

    private String content;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<Citation> citations;

    private LocalDateTime createdAt;
}
