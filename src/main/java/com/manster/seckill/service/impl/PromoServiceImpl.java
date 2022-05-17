package com.manster.seckill.service.impl;

import com.manster.seckill.dao.PromoDOMapper;
import com.manster.seckill.entity.PromoDO;
import com.manster.seckill.service.ItemService;
import com.manster.seckill.service.PromoService;
import com.manster.seckill.service.model.ItemModel;
import com.manster.seckill.service.model.PromoModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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
