package com.wyq.agent.online_agent.enums;

public class BizError {

    long code;

    String message;

    public BizError (long code, String message) {
        this.code = code;
        this.message = message;
    }

    public static final BizError SUCCESS = new BizError(0L, "SUCCESS");

    public static final BizError DEFAULT_ERROR = new BizError(-1L, "DEFAULT_ERROR");

    // 业务错误码：-50XX

}
