package com.manster.seckill.mq;

import com.alibaba.fastjson.JSON;
import com.manster.seckill.dao.StockLogDOMapper;
import com.manster.seckill.entity.StockLogDO;
import com.manster.seckill.error.BusinessException;
import com.manster.seckill.service.OrderService;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * @author wxh
 * @date 2022-05-16 20:09
 */
@Component
public class MqProducer {

    private DefaultMQProducer producer;

    private TransactionMQProducer transactionMQProducer;

    @Value("${mq.nameserver.addr}")
    private String nameAddr;

    @Value("${mq.topicname}")
    private String topicName;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    @PostConstruct
    public void init() throws MQClientException {
        //做mq producer的初始化。
        //producer的groupname是没有意义的，只是一个标志性的存在
        producer = new DefaultMQProducer("producer_group");
        producer.setNamesrvAddr(nameAddr);
        producer.start();

        transactionMQProducer = new TransactionMQProducer("transaction_producer_group");
        transactionMQProducer.setNamesrvAddr(nameAddr);
        transactionMQProducer.start();

        transactionMQProducer.setTransactionListener(new TransactionListener() {
            @Override
//          这里有三种返回状态
//          COMMIT_MESSAGE：之前的prepare消息转化为commit消息，给消费方消费
//          ROLLBACK_MESSAGE：回滚，相当于没发
//          UNKNOW：不知道什么状态，prepare消息维护在消息中间件内存内
//          这个方法是执行发送mq prepare和commit消息之间要执行的本地事务的代码
            public LocalTransactionState executeLocalTransaction(Message message, Object arg) {
                //真正要做的事：创建订单
                Integer itemId = (Integer) ((Map) arg).get("itemId");
                Integer promoId = (Integer) ((Map) arg).get("promoId");
                Integer userId = (Integer) ((Map) arg).get("userId");
                Integer amount = (Integer) ((Map) arg).get("amount");
                String stockLogId = (String) ((Map) arg).get("stockLogId");
                try {
                    //这个操作有明确的撤回，提交的状态时，执行下面的逻辑
                    //但是如果没有一个明确的返回成功或失败状态的时候，比如createOrder方法事务提交已经成功了，却恰好停在了下面这行代码这一步
                    //没有走到下面的逻辑的时候，对应的就应该是一个UNKNOW状态。
                    //那么消息中间件就会定期的往producer回调下面的checkLocalTransaction方法。
                    orderService.createOrder(userId, itemId, promoId, amount, stockLogId);
                } catch (BusinessException e) {
                    e.printStackTrace();
                    //抛出异常，认定事务需要回滚
                    //设置对应的stockLog为回滚状态
                    StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            @Override
            //走到下面这个方法有两种情况
            //1：程序断了，永远都不回返回，程序走不下去
            //2：上面的creatOrder执行了很长时间，消息中间件一直收不到消息，回调这个方法
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                //根据是否扣减库存成功，来判断要返回COMMIT，ROLLBACK还是继续UNKNOWN
                String jsonString = new String(msg.getBody());
                //将对应的jsonString转成一个map，因为传过来的是个map
                Map<String, Object> map = JSON.parseObject(jsonString, Map.class);
                Integer itemId = (Integer) map.get("itemId");
                Integer amount = (Integer) map.get("amount");
                String stockLogId = (String) map.get("stockLogId");
                StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                //这种情况一般不会发生
                if(stockLogDO==null){
                    return LocalTransactionState.UNKNOW;
                }
                if(stockLogDO.getStatus().intValue()==2){
                    //成功，可以去扣减库存
                    return LocalTransactionState.COMMIT_MESSAGE;
                }else if(stockLogDO.getStatus().intValue()==1){
                    //还是init的状态，代表creatOrder的方法还没有正常返回
                    return LocalTransactionState.UNKNOW;
                }
                //==3，rollback
                return LocalTransactionState.ROLLBACK_MESSAGE;
            }
        });
    }

    //事务型同步库存扣减消息
    public boolean transactionAsyncReduceStock(Integer userId, Integer itemId, Integer promoId, Integer amount,
                                               String stockLogId) {
        //用来扣减库存
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);
        bodyMap.put("stockLogId", stockLogId);

        //传递消息参数
        Map<String, Object> argsMap = new HashMap<>();
        argsMap.put("itemId", itemId);
        argsMap.put("amount", amount);
        argsMap.put("userId", userId);
        argsMap.put("promoId", promoId);
        argsMap.put("stockLogId", stockLogId);

        Message message = new Message(topicName, "increase",
                JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
        TransactionSendResult sendResult = null;
        try {
            //发送的是一个事务型消息，有一个二阶段提交的概念
            //发出去之后，broker的确可以收到，但是状态不是可被消费状态，是一个prepare状态。
            //此状态下这条消息是不会被消费者消费的。会去执行上面客户端这里对应的executeLocalTransaction方法
            sendResult = transactionMQProducer.sendMessageInTransaction(message, argsMap);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        }
        if (sendResult.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE) {
            return false;
        } else if (sendResult.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE) {
            return true;
        } else {
            return false;
        }
    }

    //同步库存扣减消息
    public boolean asyncReduceStock(Integer itemId, Integer amount) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);

        Message message = new Message(topicName, "increase",
                JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
        try {
            //不管三七二十一，直接投递出去
            producer.send(message);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        } catch (RemotingException e) {
            e.printStackTrace();
            return false;
        } catch (MQBrokerException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

}
