package com.wyq.agent.online_agent.application;

import com.wyq.agent.online_agent.domain.model.dto.BaseResp;
import com.wyq.agent.online_agent.domain.model.dto.MessageListResp;
import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import com.wyq.agent.online_agent.domain.model.session.Session;
import com.wyq.agent.online_agent.domain.service.message.MessageService;
import com.wyq.agent.online_agent.domain.service.session.SessionService;
import com.wyq.agent.online_agent.enums.BizError;
import org.apache.logging.log4j.util.Strings;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.wyq.agent.online_agent.enums.BizError.*;

/**
 * 会话消息查询application。
 *
 * <p>与 {@link SessionApplication} 同属 /agent/session 前缀：消息是会话下的子资源，
 * 故路径为 /agent/session/{sessionId}/messages。</p>
 */
@RestController
@RequestMapping("/agent/session")
public class MessageApplication {

    /** 消息列表默认每页条数 */
    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 50;

    /** 每页条数上限，防止一次拉取过多 */
    private static final int MAX_PAGE_SIZE = 200;

    @Autowired
    MessageService messageService;

    @Autowired
    SessionService sessionService;

    /**
     * 会话消息列表：GET /agent/session/{sessionId}/messages?uId=xx&page=1&pageSize=50
     *
     * <p>排序：按消息时间正序返回（老的在前）。
     * 分页语义：第 1 页 = 最近的一页。
     * 无数据时返回空数组 {@code messages: []}，不算错误；
     * sessionId 不存在时 baseResp.code = -5003（INVALID_SESSION_INFO），messages 仍为空数组。</p>
     */
    @GetMapping("/{sessionId}/messages")
    public MessageListResp listMessages(@PathVariable("sessionId") String sessionId,
                                        @RequestParam(value = "uId", required = false) String uId,
                                        @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                        @RequestParam(value = "pageSize", required = false, defaultValue = "50") Integer pageSize) {
        MessageListResp resp = new MessageListResp();
        // check
        if (Strings.isBlank(sessionId)) {
            resp.setBaseResp(new BaseResp(INVALID_SESSION_INFO.getCode(), INVALID_SESSION_INFO.getMessage()));
            return resp;
        }
        // 归一化分页参数
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        resp.setPage(safePage);
        resp.setPageSize(safePageSize);
        try {
            Session session = sessionService.findSessionBySessionId(sessionId);
            if (Objects.isNull(session)) {
                // 会话不存在：明确报错，messages 保持空数组
                resp.setBaseResp(new BaseResp(INVALID_SESSION_INFO.getCode(), INVALID_SESSION_INFO.getMessage()));
                return resp;
            }
            // uId 为可选参数：传了才校验归属，避免越权读别人的会话
            if (Strings.isNotBlank(uId) && !Objects.equals(uId, session.getUId())) {
                resp.setBaseResp(new BaseResp(INVALID_USER_INFO.getCode(), INVALID_USER_INFO.getMessage()));
                return resp;
            }
            long total = messageService.countMessages(sessionId);
            List<BizMessage> messages = messageService.listMessages(sessionId, (safePage - 1) * safePageSize, safePageSize);
            for (BizMessage message : messages) {
                resp.getMessages().add(convert2MessageItem(message));
            }
            resp.setTotal(total);
            resp.setBaseResp(BaseResp.builder().code(SUCCESS.getCode()).message(SUCCESS.getMessage()).build());
        } catch (BizError bizError) {
            resp.setBaseResp(BaseResp.builder().code(bizError.getCode()).message(bizError.getMessage()).build());
        } catch (RuntimeException e) {
            resp.setBaseResp(BaseResp.builder().code(DEFAULT_ERROR.getCode()).message(e.getMessage()).build());
        }
        return resp;
    }

    /** 页码归一：非正数一律当第 1 页 */
    private int normalizePage(Integer page) {
        return (Objects.isNull(page) || page < 1) ? 1 : page;
    }

    /** 每页条数归一：非正数取默认值，超过上限则截断 */
    private int normalizePageSize(Integer pageSize) {
        if (Objects.isNull(pageSize) || pageSize < 1) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** BizMessage 领域对象 → 消息列表项（字段与前端 ChatMessage 对齐） */
    private MessageListResp.MessageItem convert2MessageItem(BizMessage message) {
        MessageListResp.MessageItem item = new MessageListResp.MessageItem();
        item.setId(message.getMessageId());
        item.setRole(Objects.isNull(message.getType()) ? null : message.getType().name().toLowerCase(Locale.ROOT));
        item.setContent(message.getContent());
        item.setCreatedAt(message.getCreateTime());
        if (Objects.nonNull(message.getToolCalls())) {
            for (AssistantMessage.ToolCall toolCall : message.getToolCalls()) {
                MessageListResp.ToolCallItem callItem = new MessageListResp.ToolCallItem();
                callItem.setId(toolCall.id());
                callItem.setType(toolCall.type());
                callItem.setName(toolCall.name());
                callItem.setArguments(toolCall.arguments());
                item.getToolCalls().add(callItem);
            }
        }
        if (Objects.nonNull(message.getResponses())) {
            for (ToolResponseMessage.ToolResponse toolResponse : message.getResponses()) {
                MessageListResp.ToolResponseItem responseItem = new MessageListResp.ToolResponseItem();
                responseItem.setId(toolResponse.id());
                responseItem.setName(toolResponse.name());
                responseItem.setResponse(toolResponse.responseData());
                item.getToolResponses().add(responseItem);
            }
        }
        return item;
    }
}
