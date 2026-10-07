package com.docmind.gateway;

import com.docmind.gateway.auth.entity.SysUser;
import com.docmind.gateway.auth.mapper.SysUserMapper;
import com.docmind.gateway.system.entity.Citation;
import com.docmind.gateway.system.entity.Conversation;
import com.docmind.gateway.system.entity.Message;
import com.docmind.gateway.system.mapper.ConversationMapper;
import com.docmind.gateway.system.mapper.MessageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测完整的 MyBatis insert -> select 链路
 * 验证自定义jsonb TypeHandler 在查询路径上有没有生效
 * 回归测试实体类@TableName缺失autoResultMap = true时绑定自定义handler的字段
 * insert成功但select为null的问题
 *
 * 测试用例需要 Docker 环境支持跑 Testcontainers
 */
@Testcontainers
@SpringBootTest
class PostgresJsonbTypeHandlerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("test_docmind")
            .withInitScript("schema.sql");   // 复用项目的建表脚本

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private MessageMapper messageMapper;


    @Test
    void citationsField_shouldBeCorrectlyPersistedAndQueried_throughRealMyBatisMapping() {
        // 创建一个用户
        SysUser user = new SysUser();
        user.setId("test-user-1");
        user.setUsername("测试用户1");
        user.setPasswordHash("a1234b");
        sysUserMapper.insert(user);

        // 创建一个会话
        Conversation conversation = new Conversation();
        conversation.setId("test-conv-1");
        conversation.setUserId("test-user-1");
        conversationMapper.insert(conversation);
        // 测完整的 MyBatis insert -> select 链路
        Message message = new Message();
        message.setId("test-msg-1");
        message.setConversationId("test-conv-1");
        message.setRole("assistant");
        message.setContent("测试回答");
        message.setCitations(List.of(
                new Citation("doc-1", 0, "原文片段", "test-user-1")
        ));
        message.setCreatedAt(LocalDateTime.now());

        // 1. 真实写入数据库
        messageMapper.insert(message);

        // 2. 真实查询（这一步如果 @TableName 缺少 autoResultMap=true，
        //    之前的 bug 会导致下面这行查出来的 citations 是 null）
        Message queried = messageMapper.selectById("test-msg-1");

        assertThat(queried.getCitations()).isNotNull();
        assertThat(queried.getCitations()).hasSize(1);
        assertThat(queried.getCitations().get(0).docId()).isEqualTo("doc-1");
        assertThat(queried.getCitations().get(0).text()).isEqualTo("原文片段");
    }
}