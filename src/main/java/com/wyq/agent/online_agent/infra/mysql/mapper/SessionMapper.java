package com.wyq.agent.online_agent.infra.mysql.mapper;

import com.wyq.agent.online_agent.infra.mysql.po.SessionPo;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SessionMapper {

    @Select("SELECT * FROM t_session WHERE session_id = #{sessionId} AND is_delete = 0")
    SessionPo findBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM t_session WHERE u_id = #{uId} AND is_delete = 0 order by create_time desc")
    List<SessionPo> findByUId(@Param("uId") String uId);

    @Insert("INSERT INTO t_session(session_id, session_name, model_name, u_id, access_control, "
            + "session_status, session_type, extra) "
            + "VALUES(#{sessionId}, #{sessionName}, #{modelName}, #{uId}, #{accessControl}, "
            + "#{sessionStatus}, #{sessionType}, #{extra})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SessionPo po);

    @Update("UPDATE t_session SET session_name=#{sessionName}, model_name=#{modelName}, u_id=#{uId}, "
            + "access_control=#{accessControl}, session_status=#{sessionStatus}, session_type=#{sessionType}, "
            + "extra=#{extra} WHERE session_id=#{sessionId} AND is_delete = 0")
    int update(SessionPo po);

    @Update("UPDATE t_session SET is_delete = 1 WHERE session_id = #{sessionId}")
    int delete(@Param("sessionId") String sessionId);

    @Update("UPDATE t_session SET session_name = #{sessionName} WHERE session_id = #{sessionId}")
    int name(@Param("sessionId") String sessionId, @Param("sessionName") String sessionName);

}
