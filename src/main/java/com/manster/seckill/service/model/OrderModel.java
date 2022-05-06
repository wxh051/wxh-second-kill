package com.manster.seckill.service.model;

import java.math.BigDecimal;

/**
 * 用户下单交易模型
 * @Author manster
 * @Date 2021/5/25
 **/
public class OrderModel {
    //2021052500012345
    private String id;

    //购买者ID
    private Integer userId;

    //购买的商品ID
    private Integer itemId;

    //非空表示已秒杀价格下单
    private Integer promoId;

    //购买商品的单价,promoId非空则表示秒杀价格
    private BigDecimal itemPrice;

    //购买数量
    private Integer amount;

    //购买金额,promoId非空则表示秒杀价格
    private BigDecimal orderPrice;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getItemId() {
        return itemId;
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    public BigDecimal getItemPrice() {
        return itemPrice;
    }

    public void setItemPrice(BigDecimal itemPrice) {
        this.itemPrice = itemPrice;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public BigDecimal getOrderAmount() {
        return orderPrice;
    }

    public void setOrderAmount(BigDecimal orderAmount) {
        this.orderPrice = orderAmount;
    }

    public Integer getPromoId() {
        return promoId;
    }

    public void setPromoId(Integer promoId) {
        this.promoId = promoId;
    }

    public BigDecimal getOrderPrice() {
        return orderPrice;
    }

    public void setOrderPrice(BigDecimal orderPrice) {
        this.orderPrice = orderPrice;
    }
}
