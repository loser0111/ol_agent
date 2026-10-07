SELECT VERSION();
-- init.sql —— 在 MySQL 会话中执行
CREATE DATABASE IF NOT EXISTS ol_agent DEFAULT CHARACTER SET utf8mb4;
USE ol_agent;
CREATE TABLE t_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  session_name VARCHAR(128),
  model_name VARCHAR(64),
  u_id VARCHAR(64),
  access_control INT,
  session_status INT,
  session_type INT,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_delete TINYINT(1) DEFAULT 0,
  extra VARCHAR(512),
  UNIQUE KEY uk_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 验证
SHOW TABLES;
DESC t_session;


CREATE TABLE t_message (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  message_id  VARCHAR(64),
  session_id  VARCHAR(64) NOT NULL,
  type        VARCHAR(32),
  content     TEXT,
  metadata    TEXT,
  tool_calls  TEXT,
  responses   TEXT,
  media       TEXT,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_delete   TINYINT(1) DEFAULT 0,
  extra       TEXT,
  KEY idx_session_no (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
