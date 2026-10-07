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

    /** 会话列表分页查询：按更新时间倒序（最近活跃在前），id 兜底保证同秒也能稳定排序 */
    @Select("SELECT * FROM t_session WHERE u_id = #{uId} AND is_delete = 0 "
            + "ORDER BY update_time DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<SessionPo> findByUIdPage(@Param("uId") String uId,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    /** 会话总数（分页用） */
    @Select("SELECT COUNT(*) FROM t_session WHERE u_id = #{uId} AND is_delete = 0")
    long countByUId(@Param("uId") String uId);

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
