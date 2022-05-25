package com.manster.seckill.service.impl;

import com.manster.seckill.dao.OrderDOMapper;
import com.manster.seckill.dao.SequenceDOMapper;
import com.manster.seckill.dao.StockLogDOMapper;
import com.manster.seckill.entity.OrderDO;
import com.manster.seckill.entity.SequenceDO;
import com.manster.seckill.entity.StockLogDO;
import com.manster.seckill.error.BusinessException;
import com.manster.seckill.error.EmBusinessError;
import com.manster.seckill.service.ItemService;
import com.manster.seckill.service.OrderService;
import com.manster.seckill.service.UserService;
import com.manster.seckill.service.model.ItemModel;
import com.manster.seckill.service.model.OrderModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * @Author manster
 * @Date 2021/5/26
 **/
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private ItemService itemService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderDOMapper orderDOMapper;

    @Autowired
    private SequenceDOMapper sequenceDOMapper;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) throws BusinessException {
        //1.校验下单状态，商品是否存在，用户是否合法，购买数量是否正确
//        ItemModel itemModel = itemService.getItemById(itemId);
        //修改成下面这种，减小对数据库的依赖
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if (itemModel == null) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "商品信息不存在");
        }

//
////        UserModel userModel = userService.getUserById(userId);
//        UserModel userModel = userService.getUserByIdInCache(userId);
//        if (userModel == null) {
//            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "用户信息不存在");
//        }

        if (amount <= 0 || amount > 99) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "数量信息不正确");
        }

        //校验活动信息
//        if (promoId != null) {
//            //校验对应活动是否存在这个适用商品
//            if (!promoId.equals(itemModel.getPromoModel().getId())) {
//                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "活动信息不正确");
//            } else if (itemModel.getPromoModel().getStatus() != 2) {
//                //活动是否正在进行中
//                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "活动还未开始");
//            }
//        }

        //2.落单减库存，支付减库存
        boolean result = itemService.decreaseStock(itemId, amount);
        if (!result) {
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }

        //3.订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setItemId(itemId);
        orderModel.setUserId(userId);
        orderModel.setAmount(amount);
        if (promoId != null) {
            //商品价格取特价
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());

            redisTemplate.opsForValue().set("order:userId_" + userId + "_itemId_" + itemId, true);
            DateTime endTime = itemModel.getPromoModel().getEndDate();
            DateTime curTime = DateTime.now();
            redisTemplate.expire("order:userId_" + userId + "_itemId_" + itemId,
                    endTime.toDate().getTime() - curTime.toDate().getTime(), TimeUnit.MILLISECONDS);
        } else {
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderAmount(orderModel.getItemPrice().multiply(new BigDecimal(amount)));

        //生成交易流水号
        orderModel.setId(generateOrderNo());

        OrderDO orderDO = convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);

        //加上商品的销量
        itemService.increaseSales(itemId, amount);

        //设置库存流水状态为成功
        //因为下单操作和设置流水状态为成功是在同一个事务内。不会出现下单成功，库存流水设置失败的情况
        StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if (stockLogDO == null) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }
        stockLogDO.setStatus(2);
        stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);

//        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
//            @Override
//            //这个方法会在最近的一个@Transaction标签被成功commit后再执行
//            //把更新库存加在里面，一旦commit成功，便发送异步消息
//            //但是存在一个问题，一旦发送消息失败，就没法回滚库存。
//            public void afterCommit(){
//                //异步更新库存
//                boolean mqResult = itemService.asyncDecreaseStock(itemId, amount);
//                /*if(!mqResult){
//                    itemService.increaseStock(itemId,amount);
//                    throw new BusinessException(EmBusinessError.MQ_SEND_FAIL);
//                }*/
//            }
//        });

        //4.返回前端
        return orderModel;
    }

    @Override
    //设计事务为下面这种方式。代表下表这个事务提交后不影响调用它的外部的事务
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateOrderNo() {
        //订单号16位
        StringBuilder stringBuilder = new StringBuilder();
        //前8位为 年月日
        LocalDateTime now = LocalDateTime.now();
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-", "");
        stringBuilder.append(nowDate);

        //中间6位为自增序列
        //获取当前sequence
        int sequence = 0;
        /*
         * 查询语句getSequenceByName后面增加 for update，数据库在查询过程中给数据表增加排他锁（InnoDb引擎在加锁的时候，
         * 只有通过索引进行检索的时候才会使用行级锁，否则会使用表级锁。我们希望使用行级锁，就要给method_name添加索引，
         * 这个索引一定要创建成唯一索引，否则会出现多个重载方法之间无法同时访问的问题）当某条记录被加上排他锁之后，
         * 其他线程无法再在该行记录上增加排他锁。
         *
         * */
        SequenceDO sequenceDO = sequenceDOMapper.getSequenceByName("order_info");
        sequence = sequenceDO.getCurrentValue();
        sequenceDO.setCurrentValue(sequenceDO.getCurrentValue() + sequenceDO.getStep());
        sequenceDOMapper.updateByPrimaryKey(sequenceDO);

        //凑足6位
        String sequenceStr = String.valueOf(sequence);
        for (int i = 0; i < 6 - sequenceStr.length(); i++) {
            stringBuilder.append(0);
        }
        stringBuilder.append(sequence);


        //后两位为分库分表位
        stringBuilder.append("00");

        return stringBuilder.toString();
    }

    private OrderDO convertFromOrderModel(OrderModel orderModel) {
        if (orderModel == null) {
            return null;
        }
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel, orderDO);
        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDO.setOrderPrice(orderModel.getOrderAmount().doubleValue());
        return orderDO;
    }

}
