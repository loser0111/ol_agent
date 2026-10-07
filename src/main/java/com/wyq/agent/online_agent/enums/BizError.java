package com.wyq.agent.online_agent.enums;

import lombok.Getter;

@Getter
public class BizError extends Exception {

    long code;

    String message;

    public BizError (long code, String message) {
        this.code = code;
        this.message = message;
    }

    public static final BizError SUCCESS = new BizError(0L, "SUCCESS");

    public static final BizError DEFAULT_ERROR = new BizError(-1L, "DEFAULT_ERROR");

    // 业务错误码：-50XX
    public static final BizError INVALID_REQ_PARAMETER = new BizError(-5001, "invalid request parameter");
    public static final BizError INVALID_USER_INFO = new BizError(-5002, "invalid uid");
    public static final BizError INVALID_MODEL_NAME = new BizError(-5003, "invalid model name");
    public static final BizError INVALID_ACCESS_MODE = new BizError(-5003, "invalid access mode");
    public static final BizError INVALID_SESSION_INFO = new BizError(-5003, "invalid session info");
}
