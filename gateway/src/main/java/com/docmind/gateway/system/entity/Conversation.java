package com.docmind.gateway.system.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

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
public class Conversation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String title;

    private String userId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
