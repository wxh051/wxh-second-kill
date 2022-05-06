package com.manster.seckill.error;

/**
 * @Author manster
 * @Date 2021/5/23
 **/
public interface CommonError {
    int getErrCode();
    String getErrMsg();
    CommonError setErrMsg(String errMsg);

}
