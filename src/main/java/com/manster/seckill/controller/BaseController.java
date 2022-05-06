package com.manster.seckill.controller;

/**
 * @Author manster
 * @Date 2021/5/23
 **/
//定义一个通用的controller，处理异常
//basecontroller因为是controller的基类 无法处理进不了controller的异常 比如404找不到处理的controller
// 因此需要用exceptionhandler
public class BaseController {

    public static final String CONTENT_TYPE_FORMED = "application/x-www-form-urlencoded";

    //通过springboot自带的springmvc handlerException解决一个通用的异常处理的方式
    //controller层的异常被处理掉，符合一个spring钩子的一个处理思想
    //定义exceptionhandler解决未被controller层吸收的exception
//    @ExceptionHandler(Exception.class)
//    @ResponseStatus(HttpStatus.OK)
//    @ResponseBody  //需要加，因为这种处理方式返回object其实会寻找本地路径下的页面文件
//    public Object handlerException(HttpServletRequest request, Exception ex){
//        Map<String, Object> responseData = new HashMap<>();
//        if(ex instanceof BusinessException){
//            BusinessException businessException = (BusinessException) ex;
//            responseData.put("errCode", businessException.getErrCode());
//            responseData.put("errMsg", businessException.getErrMsg());
//        }else {
//            responseData.put("errCode", EmBusinessError.UNKNOWN_ERROR.getErrCode());
//            responseData.put("errMsg", EmBusinessError.UNKNOWN_ERROR.getErrMsg());
//        }
//        return CommonReturnType.create(responseData,"fail");
//    }
}
