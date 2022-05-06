package com.manster.seckill.service;

import com.manster.seckill.error.BusinessException;
import com.manster.seckill.service.model.ItemModel;

import java.util.List;

/**
 * @Author manster
 * @Date 2021/5/24
 **/
public interface ItemService {

    //创建商品
    ItemModel createItem(ItemModel itemModel) throws BusinessException;

    //商品列表浏览
    List<ItemModel> listItem();

    //商品详情浏览
    ItemModel getItemById(Integer id);

    //库存扣减
    boolean decreaseStock(Integer itemId, Integer amount);

    //销量增加
    void increaseSales(Integer itemId, Integer amount);

}
