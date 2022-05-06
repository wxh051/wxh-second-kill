package com.manster.seckill.response;

/**
 * @Author manster
 * @Date 2021/5/23
 **/
//在这定义一个通用的返回格式，也能规范化的处理一些错误
public class CommonReturnType {

    //对应请求的返回处理结果 "success"或"fail"
    private String status;
    //若status=success，返回前端需要的json数据
    //若status=fail，返回通用的错误码格式
    private Object data;

    //通用的创建方法
    public static CommonReturnType create(Object result){
        return CommonReturnType.create(result,"success");
    }

    public static CommonReturnType create(Object result, String status){
        CommonReturnType type = new CommonReturnType();
        type.setStatus(status);
        type.setData(result);
        return type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
