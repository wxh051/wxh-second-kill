package com.manster.seckill.service;

import com.manster.seckill.service.model.PromoModel;
import com.manster.seckill.service.model.UserModel;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

/**
 * @Author manster
 * @Date 2021/5/27
 **/
public interface PromoService {

    //根据商品id获取即将进行以及正在进行的活动信息
    PromoModel getPromoByItemId(Integer itemId);

    //活动发布
    void publishPromo(Integer promoId);

    //生成秒杀用的令牌
    String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId);

    //获取秒杀地址
    String createPath(UserModel userModel, Integer itemId) throws UnsupportedEncodingException, NoSuchAlgorithmException;

    //校验秒杀地址
    boolean checkPath(UserModel userModel, Integer itemId, String path);
}
