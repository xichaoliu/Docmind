package com.docmind.gateway.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.docmind.gateway.common.exception.BizException;
import com.docmind.gateway.common.security.CurrentUser;
import com.docmind.gateway.system.entity.Conversation;
import com.docmind.gateway.system.entity.Message;
import com.docmind.gateway.system.mapper.ConversationMapper;
import com.docmind.gateway.system.mapper.MessageMapper;
import com.docmind.gateway.system.service.ConversationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
@RequiredArgsConstructor
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements ConversationService {

    private final MessageMapper messageMapper;   // 删除会话时要连带删消息

    @Override
    public Conversation create(String firstQuestion) {
        Conversation conversation = new Conversation();
        // 用问题的前一部分当作会话标题，类似 ChatGPT 的做法
        String title = firstQuestion.length() > 20
                ? firstQuestion.substring(0, 20) + "..."
                : firstQuestion;
        conversation.setTitle(title);
        conversation.setUserId(CurrentUser.require());
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());

        this.save(conversation);
        log.debug("新建会话：id={}, title={}", conversation.getId(), title);
        return conversation;
    }
    @Override
    public List<Conversation> listAll() {
        return this.list(
                Wrappers.<Conversation>lambdaQuery()
                        .eq(Conversation::getUserId, CurrentUser.require())
                        .orderByDesc(Conversation::getUpdatedAt)
        );
    }

    @Override
    public void updateTitle(String conversationId, String title) {
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setTitle(title);
        conversation.setUpdatedAt(LocalDateTime.now());
        this.updateById(conversation);
    }

    @Override
    @Transactional   // 两张表都要改，加事务保证要么都成功要么都失败
    public void deleteById(String conversationId) {
        Conversation conv = this.getById(conversationId);
        if (conv == null || !conv.getUserId().equals(CurrentUser.require())) {
            throw new BizException(401, "无权操作该会话");
        }
        messageMapper.delete(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
        );
        this.removeById(conversationId);
        log.debug("删除会话及其消息：conversationId={}", conversationId);
    }
}
