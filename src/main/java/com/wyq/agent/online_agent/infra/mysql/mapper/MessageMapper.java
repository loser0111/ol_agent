package com.wyq.agent.online_agent.infra.mysql.mapper;

import com.wyq.agent.online_agent.infra.mysql.po.MessagePo;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MessageMapper {

    /** 按会话查历史（按 no 升序，供模型回放） */
    @Select("SELECT * FROM t_message WHERE session_id = #{sessionId} AND is_delete = 0 ORDER BY id ASC")
    List<MessagePo> findBySessionId(@Param("sessionId") String sessionId);

    // ① MessageMapper：占位符与 Po 字段对齐
    @Insert("INSERT INTO t_message(message_id, session_id, type, content, metadata, "
            + "tool_calls, responses, media, extra) "
            + "VALUES(#{messageId}, #{sessionId}, #{type}, #{content}, #{metadata}, "
            + "#{toolCalls}, #{responses}, #{media}, #{extra})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MessagePo po);

    /** 删除会话全部消息（软删） */
    @Update("UPDATE t_message SET is_delete = 1 WHERE session_id = #{sessionId}")
    int deleteBySessionId(@Param("sessionId") String sessionId);

    /** 删除会话全部消息（软删） */
    @Update("UPDATE t_message SET is_delete = 1 WHERE message_id = #{messageId}")
    int deleteByMessageId(@Param("messageId") String messageId);
}