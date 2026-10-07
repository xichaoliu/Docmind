package com.docmind.gateway.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.docmind.gateway.system.entity.Citation;
import com.docmind.gateway.system.entity.Message;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author xichaoliu
 * @since 2026-09-24
 */
public interface MessageService extends IService<Message> {

    /**
     * 保存一条用户消息
     */
    Message saveUserMessage(String conversationId, String content);

    /**
     * 保存一条助手回复消息（生成完成后调用，附带引用来源）
     */
    Message saveAssistantMessage(String conversationId, String content, List<Citation> citations);

    /**
     * 按会话查历史消息，按时间正序排列
     */
    List<Message> listByConversation(String conversationId);

    List<Message> listRecent(String conversationId, int limit);
}