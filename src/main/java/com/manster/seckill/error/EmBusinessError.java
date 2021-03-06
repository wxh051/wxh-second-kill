package com.manster.seckill.error;

/**
 * @Author manster
 * @Date 2021/5/23
 **/
public enum EmBusinessError implements CommonError {
    //通用错误类型10001
    PARAMETER_VALIDATION_ERROR(10001, "参数不合法"),
    //未知错误类型
    UNKNOWN_ERROR(10002, "未知异常"),

    //20000开头为用户信息相关错误
    USER_NOT_EXIST(20001, "用户不存在"),
    USER_LOGIN_FAIL(20002, "用户手机号或密码不正确"),
    USER_NOT_LOGIN(20003, "用户未登录"),

    //30000开头为交易信息错误定义
    STOCK_NOT_ENOUGH(30001, "库存不足"),
    MQ_SEND_FAIL(30002,"库存异步消息失败"),
    RATELIMIT(30003,"活动太火爆，请稍后再试"),
    REQUEST_ILLEGAL(30004,"请求非法，请重新尝试"),
    REPEAT_ILLEGAL(30005,"重复购买，请求非法"),
    ;

    private EmBusinessError(int errCode, String errMsg){
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    private int errCode;
    private String errMsg;
    @Override
    public int getErrCode() {
        return this.errCode;
    }

    @Override
    public String getErrMsg() {
        return this.errMsg;
    }

    @Override
    public CommonError setErrMsg(String errMsg) {
        this.errMsg = errMsg;
        return this;
    }
}
