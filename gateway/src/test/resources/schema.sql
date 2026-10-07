-- schema.sql —— 全量重建脚本，仅用于开发环境（会清空数据）
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

DROP TABLE IF EXISTS message      CASCADE;
DROP TABLE IF EXISTS conversation CASCADE;
DROP TABLE IF EXISTS document     CASCADE;
DROP TABLE IF EXISTS sys_user     CASCADE;

-- 被引用的表必须先建
CREATE TABLE sys_user (
    id            VARCHAR(32)  PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(64),
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE document (
    id          VARCHAR(32) PRIMARY KEY,
    user_id     VARCHAR(32) NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    doc_id      VARCHAR(64) NOT NULL UNIQUE,
    file_name   VARCHAR(255) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PROCESSING / READY / FAILED',
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE conversation (
    id          VARCHAR(32) PRIMARY KEY,
    user_id     VARCHAR(32) NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    title       VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE message (
    id              VARCHAR(32) PRIMARY KEY,
    conversation_id VARCHAR(32) NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL, -- user / assistant
    content         TEXT NOT NULL,
    citations       JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_message_conversation_id ON message(conversation_id);
CREATE INDEX idx_document_status         ON document(status);
CREATE INDEX idx_conversation_user_id    ON conversation(user_id);
CREATE INDEX idx_document_user_id        ON document(user_id);