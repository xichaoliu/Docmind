package com.docmind.gateway.system.controller;
import com.docmind.gateway.common.result.Result;
import com.docmind.gateway.system.entity.Conversation;
import com.docmind.gateway.system.entity.Message;
import com.docmind.gateway.system.service.ConversationService;
import com.docmind.gateway.system.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;



/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author xichaoliu
 * @since 2026-09-24
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationService conversationService;
    private final MessageService messageService;

    /**
     * 会话列表
     */
    @GetMapping
    public Result<List<Conversation>> list() {
        return Result.success(conversationService.listAll());

    }

    /**
     * 单个会话详情
     */
    @GetMapping("/{id}")
    public Result<Conversation>getOne(@PathVariable String id) {
        return Result.success(conversationService.getById(id));
    }

    /**
     * 某个会话的历史消息
     */
    @GetMapping("/{id}/messages")
    public Result<List<Message>> getMessages(@PathVariable String id) {

        return Result.success(messageService.listByConversation(id));
    }

    /**
     * 更新会话标题
     */
    @PatchMapping("/{id}")
    public Result<Void> updateTitle(@PathVariable String id, @RequestBody Map<String, String> body) {
        conversationService.updateTitle(id, body.get("title"));
        return Result.success();
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/{id}")
    public Result<Void>delete(@PathVariable String id) {
        conversationService.deleteById(id);
        return Result.success();
    }

}
