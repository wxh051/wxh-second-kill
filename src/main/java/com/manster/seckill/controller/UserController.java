package com.manster.seckill.controller;

import com.alibaba.druid.util.StringUtils;
import com.manster.seckill.controller.vo.UserVO;
import com.manster.seckill.error.BusinessException;
import com.manster.seckill.error.EmBusinessError;
import com.manster.seckill.response.CommonReturnType;
import com.manster.seckill.service.UserService;
import com.manster.seckill.service.model.UserModel;
import com.manster.seckill.util.UUIDUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import sun.misc.BASE64Encoder;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @Author manster
 * @Date 2021/5/23
 **/
@RestController
@RequestMapping("/user")
//@CrossOrigin注解，Springboot自动在响应头上加上跨域允许
@CrossOrigin(allowCredentials = "true", allowedHeaders = "*")
public class UserController extends BaseController {
    @Autowired
    private UserService userService;

    //这个通过Spring Bean包装的httpServletRequest
    //它的内部拥有ThreadLocal方式的map，去让用户在每个线程中处理自己对应的request，并且有ThreadLocal清除的机制
    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;

    //用户登录接口
    @PostMapping(value = "/login", consumes = {CONTENT_TYPE_FORMED})
    public CommonReturnType login(@RequestParam(name = "telphone") String telphone,
                                  @RequestParam(name = "password") String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        //入参校验
        if (org.apache.commons.lang3.StringUtils.isEmpty(telphone) ||
                org.apache.commons.lang3.StringUtils.isEmpty(password)) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "请登录后下单");
        }

        //用户登录服务
        UserModel userModel = userService.vaildateLogin(telphone, EncodeByMd5(password));

        //将登录凭证加入到用户登录成功的session内
        //修改成若用户登录验证成功后将对应的登录信息和登录凭证一起存入redis中

        //生成登录凭证token，采取UUID（保证token登录凭证的唯一性，任何一个用户的登录凭证是唯一的）的形式
        String uuidToken = UUIDUtil.uuid();
        //建立token和用户登录态之间的联系
        redisTemplate.opsForValue().set(uuidToken, userModel);
        redisTemplate.expire(uuidToken, 1, TimeUnit.HOURS);

//        httpServletRequest.getSession().setAttribute("IS_LOGIN", true);
//        httpServletRequest.getSession().setAttribute("LOGIN_USER", userModel);
        //下发了token
        return CommonReturnType.create(uuidToken);
    }

    //用户注册接口
    @PostMapping(value = "/register", consumes = {CONTENT_TYPE_FORMED})
    public CommonReturnType register(@RequestParam(name = "telphone") String telphone,
                                     @RequestParam(name = "otpCode") String otpCode,
                                     @RequestParam(name = "name") String name,
                                     @RequestParam(name = "gender") Integer gender,
                                     @RequestParam(name = "age") Integer age,
                                     @RequestParam(name = "password") String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        //验证手机号和对应的otpcode是否相符合
        String inSessionOtpCode = (String) this.httpServletRequest.getSession().getAttribute(telphone);
        if (!StringUtils.equals(otpCode, inSessionOtpCode)) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "短信验证码不符合");
        }

        //用户注册
        UserModel userModel = new UserModel();
        userModel.setName(name);
        userModel.setGender(new Byte(String.valueOf(gender)));
        userModel.setAge(age);
        userModel.setTelphone(telphone);
        userModel.setRegisterMode("byphone");
        userModel.setEncrptPassword(EncodeByMd5(password));

        userService.register(userModel);

        return CommonReturnType.create(null);
    }

    public String EncodeByMd5(String str) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        //确定计算方法
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        BASE64Encoder base64Encoder = new BASE64Encoder();
        //加密字符串
        String newstr = base64Encoder.encode(md5.digest(str.getBytes("utf-8")));
        return newstr;
    }

    /*
    * 基础能力建设
            springboot + MVC + mybatis 框架搭建，外加常态的错误异常定义、正确的返回值类型定义。
      模型能力管理
            领域模型管理（如 user 对象就是一个用户领域的一个模型），包括完整的生命周期。用户模型、商品模型、秒杀模型等。
                用户信息管理：
                    otp 短信获取
                    otp 注册用户
                    用户手机号登录

    1. 用户获取 otp 短信验证码
        a. 需要按照一定的规则生产OTP 验证码
        b. 将 OTP 验证码通对应用户的手机号关联（一般使用Redis处理，此处采用 session 模仿实现）
            使用 spring 注入方式注入一个 HttpServletRequest 对象，该对象其实是通过 spring bean 包装的 request 对象本质是 proxy 模式
            （spring 在注入 HttpServletRequest 时，发现如果注入的是 一个 ObjectFactory 类型的对象时，就会将注入的 bean 替换成一个 JDK
            动态代理对象，代理对象在执行 HttpServletRequest 对象里的方法时，就会通过 RequestObjectFactory.getObject() 获取一个
            新的 request 对象来执行。），即多例模式?。
            Spring能实现在多线程环境下，将各个线程的request进行隔离，且准确无误的进行注入，奥秘就是ThreadLocal. 它的内部拥有 ThreadLocal
            方式的 map，去让用户在每个线程中处理自己对应的 request 中的数据，并且有ThreadLocal清除的机制。
        c. 将 OTP 验证码通关短信通道发送给用户
        * */
    //获取验证码
    //consumes就是后端对应的contentType的名字
    @PostMapping(value = "/getotp", consumes = {CONTENT_TYPE_FORMED})
    public CommonReturnType getOtp(@RequestParam(name = "telphone") String telphone) {
        //需要按照一定规则生成OTP验证码
        Random random = new Random();
        int randomInt = random.nextInt(99999);
        randomInt += 10000;
        String otpCode = String.valueOf(randomInt);

        //将otp验证码同对应的用户手机号关联,使用httpsession的方式绑定手机号与OTPCODE
        httpServletRequest.getSession().setAttribute(telphone, otpCode);

        //将otp验证码通过短信通道发送给用户（省略）
        System.out.println("telphone = " + telphone + "& otpCode = " + otpCode);
        return CommonReturnType.create(null);
    }

    @GetMapping("/get")
    public CommonReturnType getUser(@RequestParam(name = "id") Integer id) throws BusinessException {
        UserModel userModel = userService.getUserById(id);
        //将核心领域模型用户对象转换为可供UI使用的viewObject
        UserVO userVO = convertFromModel(userModel);

        //若获取的对应用户信息不存在
        if (userModel == null) {
            throw new BusinessException(EmBusinessError.USER_NOT_EXIST);
//            userModel.setEncrptPassword("123");
        }

        //返回通用对象
        return CommonReturnType.create(userVO);
    }

    /**
     * 将核心领域模型转为视图对象
     *
     * @param userModel 领域模型
     * @return 视图对象
     */
    private UserVO convertFromModel(UserModel userModel) {
        if (userModel == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(userModel, userVO);
        return userVO;
    }

}
