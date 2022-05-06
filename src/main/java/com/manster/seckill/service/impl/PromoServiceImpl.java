package com.manster.seckill.service.impl;

import com.manster.seckill.dao.PromoDOMapper;
import com.manster.seckill.entity.PromoDO;
import com.manster.seckill.service.PromoService;
import com.manster.seckill.service.model.PromoModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);
        //entity->model
        PromoModel promoModel = convertFromEntity(promoDO);
        if(promoModel == null){
            return null;
        }

        //判断当前时间活动是否即将开始或正在进行
        if(promoModel.getStartDate().isAfterNow()){
            //活动未开始
            promoModel.setStatus(1);
        }else if (promoModel.getEndDate().isBeforeNow()){
            //活动已结束
            promoModel.setStatus(3);
        }else {
            //正在进行中
            promoModel.setStatus(2);
        }

        return promoModel;
    }

    private PromoModel convertFromEntity(PromoDO promoDO){
        if(promoDO == null){
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
