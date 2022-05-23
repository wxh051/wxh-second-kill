package com.manster.seckill.service.impl;

import com.manster.seckill.dao.ItemDOMapper;
import com.manster.seckill.dao.ItemStockDOMapper;
import com.manster.seckill.dao.StockLogDOMapper;
import com.manster.seckill.entity.ItemDO;
import com.manster.seckill.entity.ItemStockDO;
import com.manster.seckill.entity.StockLogDO;
import com.manster.seckill.error.BusinessException;
import com.manster.seckill.error.EmBusinessError;
import com.manster.seckill.mq.MqProducer;
import com.manster.seckill.service.ItemService;
import com.manster.seckill.service.PromoService;
import com.manster.seckill.service.model.ItemModel;
import com.manster.seckill.service.model.PromoModel;
import com.manster.seckill.util.UUIDUtil;
import com.manster.seckill.validator.ValidationResult;
import com.manster.seckill.validator.ValidatorImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author manster
 * @Date 2021/5/24
 **/
@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ItemDOMapper itemDOMapper;

    @Autowired
    private ItemStockDOMapper itemStockDOMapper;

    @Autowired
    private PromoService promoService;

    @Autowired
    private ValidatorImpl validator;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MqProducer mqProducer;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    @Autowired
    private RedisScript<Long> script;

    /**
     * 将商品领域模型转为orm映射对象
     *
     * @param itemModel 领域模型
     * @return 数据对象
     */
    private ItemDO convertItemDOFromItemModel(ItemModel itemModel) {
        if (itemModel == null) {
            return null;
        }
        ItemDO itemDO = new ItemDO();
        BeanUtils.copyProperties(itemModel, itemDO);
        //BeanUtils不会copy不同类型的属性，价格需要我们自己来
        itemDO.setPrice(itemModel.getPrice().doubleValue());

        return itemDO;
    }

    /**
     * 将库存领域模型转为orm映射对象
     *
     * @param itemModel 领域模型
     * @return 库存数据对象
     */
    private ItemStockDO convertItemStockDOFromItemModel(ItemModel itemModel) {
        if (itemModel == null) {
            return null;
        }
        ItemStockDO itemStockDO = new ItemStockDO();
        itemStockDO.setItemId(itemModel.getId());
        itemStockDO.setStock(itemModel.getStock());

        return itemStockDO;
    }

    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {
        //校验入参
        ValidationResult result = validator.validate(itemModel);
        if (result.isHasErrors()) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, result.getErrMsg());
        }
        //转化itemmodel -> entity
        ItemDO itemDO = convertItemDOFromItemModel(itemModel);

        //写入数据库
        System.out.println(itemDO.getId());
        itemDOMapper.insertSelective(itemDO);
        System.out.println(itemDO.getId());
        itemModel.setId(itemDO.getId());

        ItemStockDO itemStockDO = convertItemStockDOFromItemModel(itemModel);
        itemStockDOMapper.insertSelective(itemStockDO);
        //返回创建完成的对象
        return getItemById(itemModel.getId());
    }

    @Override
    public List<ItemModel> listItem() {
        List<ItemDO> itemDOList = itemDOMapper.listItem();
        List<ItemModel> itemModelList = itemDOList.stream().map(itemDO -> {
            ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
            ItemModel itemModel = convertModelFromEntity(itemDO, itemStockDO);
            return itemModel;
        }).collect(Collectors.toList());
        return itemModelList;
    }

    @Override
    public ItemModel getItemById(Integer id) {
        ItemDO itemDO = itemDOMapper.selectByPrimaryKey(id);
        if (itemDO == null) {
            return null;
        }
        //操作获得库存
        ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());

        //将entity -> model
        ItemModel itemModel = convertModelFromEntity(itemDO, itemStockDO);

        //获取活动商品信息
        PromoModel promoModel = promoService.getPromoByItemId(itemModel.getId());
        //该商品有活动且活动未结束
        if (promoModel != null && promoModel.getStatus() != 3) {
            itemModel.setPromoModel(promoModel);
        }

        return itemModel;
    }

    @Override
    public ItemModel getItemByIdInCache(Integer id) {
        ItemModel itemModel = (ItemModel) redisTemplate.opsForValue().get("item_validate_" + id);
        if (itemModel == null) {
            itemModel = this.getItemById(id);
            redisTemplate.opsForValue().set("item_validate_" + id, itemModel);
            //设置有效期
            redisTemplate.expire("item_validate_" + id, 10, TimeUnit.MINUTES);
        }
        return itemModel;
    }

    @Override
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) throws BusinessException {
        //int affectedRow = itemStockDOMapper.decreaseStock(itemId, amount);

        //这里自动拆箱有隐式的java空指针异常;严谨一点的写法应该在入口层面判空后异常退出
//        long result = redisTemplate.opsForValue().increment("promo_item_stock_" + itemId, amount.intValue() * -1);

        //这里自己优化了一下，不用上面的减redis库存的方式。通过lua脚本实现一个分布式锁
        Long result= (Long) redisTemplate.execute(script, Collections.singletonList("promo_item_stock_" + itemId),
                Collections.EMPTY_LIST);

        //发送一条消息出去，让异步消息队列感知到，去减库存
        if (result > 0) {
            //更新库存成功
            return true;
        } else if (result == 0) {
            //打上库存已售罄标识
            redisTemplate.opsForValue().set("promo_item_stock_invalid_" + itemId, "true");
            //更新库存成功
            return true;
        } else {
            //更新库存失败
            //result<0就代表了库存不够扣了。超卖了。所以不该扣，得加回去
            increaseStock(itemId, amount);
            return false;
        }
    }

    @Override
    public boolean increaseStock(Integer itemId, Integer amount) {
        redisTemplate.opsForValue().increment("promo_item_stock_" + itemId, amount.intValue());
        return true;
    }

    @Override
    //send是同步的 消费端是异步消费 所以起名async
    public boolean asyncDecreaseStock(Integer itemId, Integer amount) {
        boolean mqResult = mqProducer.asyncReduceStock(itemId, amount);
        return mqResult;
    }

    @Override
    @Transactional
    public void increaseSales(Integer itemId, Integer amount) {
        itemDOMapper.increaseSales(itemId, amount);
    }

    @Override
    @Transactional
    //初始化一条库存的流水，用来将状态设置为一个准备开始的状态，并且提交对应的事务，使得数据库内有对应的stock_log生成
    //在OrderController中下单之前调用
    public String initStockLog(Integer itemId, Integer amount) {
        StockLogDO stockLogDO = new StockLogDO();
        stockLogDO.setItemId(itemId);
        stockLogDO.setAmount(amount);
        //使用uuuid的方式创建一个stock_log_id，作为一个主键
        stockLogDO.setStockLogId(UUIDUtil.uuid());
        stockLogDO.setStatus(1);

        stockLogDOMapper.insertSelective(stockLogDO);
        return stockLogDO.getStockLogId();
    }

    private ItemModel convertModelFromEntity(ItemDO itemDO, ItemStockDO itemStockDO) {
        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(itemDO, itemModel);
        itemModel.setPrice(BigDecimal.valueOf(itemDO.getPrice()));
        itemModel.setStock(itemStockDO.getStock());

        return itemModel;
    }
}
