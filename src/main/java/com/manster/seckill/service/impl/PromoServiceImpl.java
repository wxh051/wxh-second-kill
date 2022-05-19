package com.manster.seckill.service.impl;

import com.manster.seckill.dao.PromoDOMapper;
import com.manster.seckill.entity.PromoDO;
import com.manster.seckill.service.ItemService;
import com.manster.seckill.service.PromoService;
import com.manster.seckill.service.UserService;
import com.manster.seckill.service.model.ItemModel;
import com.manster.seckill.service.model.PromoModel;
import com.manster.seckill.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @Author manster
 * @Date 2021/5/27
 **/
@Service
public class PromoServiceImpl implements PromoService {

    @Autowired
    private PromoDOMapper promoDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private UserService userService;

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);
        //entity->model
        PromoModel promoModel = convertFromEntity(promoDO);
        if (promoModel == null) {
            return null;
        }

        //判断当前时间活动是否即将开始或正在进行
        if (promoModel.getStartDate().isAfterNow()) {
            //活动未开始
            promoModel.setStatus(1);
        } else if (promoModel.getEndDate().isBeforeNow()) {
            //活动已结束
            promoModel.setStatus(3);
        } else {
            //正在进行中
            promoModel.setStatus(2);
        }

        return promoModel;
    }

    @Override
    //这个方法一般运营去调用，发布活动。本项目就在itemcontroller里用
    public void publishPromo(Integer promoId) {
        //通过活动ID获取活动
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        //活动没有适用的商品，直接return
        if (promoDO.getItemId() == null || promoDO.getItemId().intValue() == 0) {
            return;
        }
        ItemModel itemModel = itemService.getItemById(promoDO.getItemId());
        //有一个问题，商品在从数据库中读取出来后，把对应的库存存入缓存，这段时间内对应的商品是可能会被售卖的。但是可能活动还未开始
        //就没办法区别活动商品有多少个，普通商品有多少个。因此一般采用商品上下架方式。活动开始时，自动上架对应商品，若没开始，则不能售卖
        //这样就可以在业务层面解决这个问题。但是本项目仅讨论秒杀活动商品，因此暂时不实现这个逻辑，不然库存不发生变化。

        //将库存同步到redis内
        redisTemplate.opsForValue().set("promo_item_stock_" + itemModel.getId(), itemModel.getStock());

        //将大闸的限制数字设到redis内
        //大闸的限制数字为当前活动库存的五倍
        redisTemplate.opsForValue().set("promo_door_count_" + promoId, itemModel.getStock().intValue() * 5);
    }

    @Override
    public String generateSecondKillToken(Integer promoId, Integer itemId, Integer userId) {
        //判断库存是否已售罄，若对应的售罄key存在，则直接返回下单失败
        if (redisTemplate.hasKey("promo_item_stock_invalid_" + itemId)) {
            return null;
        }

        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        //entity->model
        PromoModel promoModel = convertFromEntity(promoDO);
        if (promoModel == null) {
            return null;
        }
        //判断当前时间活动是否即将开始或正在进行
        if (promoModel.getStartDate().isAfterNow()) {
            //活动未开始
            promoModel.setStatus(1);
        } else if (promoModel.getEndDate().isBeforeNow()) {
            //活动已结束
            promoModel.setStatus(3);
        } else {
            //正在进行中
            promoModel.setStatus(2);
        }
        //判断活动是否正在进行，若不是，无法生成令牌
        if (promoModel.getStatus().intValue() != 2) {
            return null;
        }
        //判断item信息是否存在
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if (itemModel == null) {
            return null;
        }
        //判断用户信息是否存在
        UserModel userModel = userService.getUserByIdInCache(userId);
        if (userModel == null) {
            return null;
        }

        //获取秒杀大闸的count数量
        long result = redisTemplate.opsForValue().increment("promo_door_count_" + promoId, -1);
        if(result<0){
            return null;
        }

        //生成token并且存入redis内，设置5分钟有效期
        String token = UUID.randomUUID().toString().replace("-", "");
        //一个用户对一个活动内的一个商品有令牌的权限
        redisTemplate.opsForValue().set("promo_token_" + promoId + "_userid_" + userId + "_itemid_" + itemId, token);
        redisTemplate.expire("promo_token_" + promoId + "_userid_" + userId + "_itemid_" + itemId, 5, TimeUnit.MINUTES);

        return token;
    }

    private PromoModel convertFromEntity(PromoDO promoDO) {
        if (promoDO == null) {
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO, promoModel);
        promoModel.setPromoItemPrice(BigDecimal.valueOf(promoDO.getPromoItemPrice()));
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDO.getEndDate()));
        return promoModel;
    }

}
