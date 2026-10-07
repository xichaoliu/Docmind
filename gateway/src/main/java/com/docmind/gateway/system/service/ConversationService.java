package com.docmind.gateway.system.service;

import com.docmind.gateway.system.entity.Conversation;
import com.baomidou.mybatisplus.extension.service.IService;
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
public interface ConversationService extends IService<Conversation> {
    /**
     * 新建会话，用问题内容截断生成标题
     */
    Conversation create(String firstQuestion);
    /**
     * 会话列表，按更新时间倒序（最近使用的排前面）
     */
    List<Conversation> listAll();

    /**
     * 更新会话标题
     */
    void updateTitle(String conversationId, String title);

    /**
     * 删除会话（连带删除该会话下的所有消息）
     */
    void deleteById(String conversationId);

}
