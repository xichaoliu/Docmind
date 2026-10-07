package com.docmind.gateway.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.docmind.gateway.system.entity.Citation;
import com.docmind.gateway.system.entity.Message;
import com.docmind.gateway.system.mapper.MessageMapper;
import com.docmind.gateway.system.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author xichaoliu
 * @since 2026-09-24
 */
@Slf4j
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    @Override
    public Message saveUserMessage(String conversationId, String content) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole("user");
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));

        this.save(message);   // IService 自带的方法，等价于 mapper.insert(message)
        log.debug("保存用户消息：conversationId={}, id={}", conversationId, message.getId());
        return message;
    }

    @Override
    public Message saveAssistantMessage(String conversationId, String content, List<Citation> citations) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole("assistant");
        message.setContent(content);
        message.setCitations(citations);   // JSONB 字段，靠 Entity 上的 @TableField(typeHandler = JacksonTypeHandler.class) 自动序列化
        message.setCreatedAt(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));

        this.save(message);
        log.debug("保存助手回复：conversationId={}, id={}, citations 数量={}",
                conversationId, message.getId(),
                citations == null ? 0 : citations.size());
        return message;
    }

    @Override
    public List<Message> listByConversation(String conversationId) {
        return this.list(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreatedAt)
        );
    }

    @Override
    public List<Message> listRecent(String conversationId, int limit) {
        List<Message> list = this.list(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByDesc(Message::getCreatedAt)
                        .last("LIMIT " + limit)      // limit 是 int，不存在注入风险
        );
        Collections.reverse(list);           // 倒序取最近的，再翻回时间正序
        return list;
    }
}
