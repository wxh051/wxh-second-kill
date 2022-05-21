package com.manster.seckill.controller;

import com.manster.seckill.annotation.MyRateLimiter;
import com.manster.seckill.controller.vo.ItemVO;
import com.manster.seckill.error.BusinessException;
import com.manster.seckill.response.CommonReturnType;
import com.manster.seckill.service.CacheService;
import com.manster.seckill.service.ItemService;
import com.manster.seckill.service.PromoService;
import com.manster.seckill.service.model.ItemModel;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author manster
 * @Date 2021/5/24
 **/

//测试限流输出日志
@Slf4j
@RestController
@RequestMapping("/item")
@CrossOrigin(allowCredentials = "true", allowedHeaders = "*")
public class ItemControlller extends BaseController {

    @Autowired
    private ItemService itemService;

    @Autowired
    //封装了对redis所有的key-value的操作
    private RedisTemplate redisTemplate;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private PromoService promoService;

    //商品创建
    @PostMapping(value = "/create", consumes = {CONTENT_TYPE_FORMED})
    public CommonReturnType createItem(@RequestParam(name = "title") String title,
                                       @RequestParam(name = "price") BigDecimal price,
                                       @RequestParam(name = "stock") Integer stock,
                                       @RequestParam(name = "description") String description,
                                       @RequestParam(name = "imgUrl") String imgUrl) throws BusinessException {
        ItemModel itemModel = new ItemModel();
        itemModel.setTitle(title);
        itemModel.setPrice(price);
        itemModel.setStock(stock);
        itemModel.setDescription(description);
        itemModel.setImgUrl(imgUrl);

        ItemModel itemModelForReturn = itemService.createItem(itemModel);

        ItemVO itemVO = convertFromModel(itemModelForReturn);

        return CommonReturnType.create(itemVO);
    }

    @MyRateLimiter(value = 1.0, timeout = 300)
    @GetMapping("/testRate")
    public CommonReturnType test1() {
        log.info("【test1】被执行了。。。。。");
        return CommonReturnType.create("别想一直看到我，不信你快速刷新看看~");
    }

    //商品页面浏览
    @GetMapping(value = "/list")
    public CommonReturnType listItem() {
        log.info("【test1】被执行了。。。。。");
        List<ItemModel> itemModelList = itemService.listItem();

        //使用stream api 将list内的 itemModel 转化为 itemVO
        List<ItemVO> itemVOList = itemModelList.stream().map(itemModel -> {
            ItemVO itemVO = convertFromModel(itemModel);
            return itemVO;
        }).collect(Collectors.toList());

        return CommonReturnType.create(itemVOList);
    }


    @GetMapping(value = "/publishpromo")
    public CommonReturnType publishpromo(@RequestParam(name = "id") Integer id) {
        promoService.publishPromo(id);
        return CommonReturnType.create(null);
    }


    //商品详情页浏览
    @GetMapping(value = "/getItem")
    public CommonReturnType getItem(@RequestParam(name = "id") Integer id) {
        ItemModel itemModel = null;

        //先取本地缓存
        itemModel = (ItemModel) cacheService.getFromCommonCache("item_" + id);

        if (itemModel == null) {
            //根据商品的ID到redis内获取
            itemModel = (ItemModel) redisTemplate.opsForValue().get("item_" + id);

            //若redis内不存在对应的itemModel，则访问下游service，到数据库中取
            if (itemModel == null) {
                itemModel = itemService.getItemById(id);
                //设置itemModel到redis内
                redisTemplate.opsForValue().set("item_" + id, itemModel);
                redisTemplate.expire("item_" + id, 10, TimeUnit.MINUTES);
            }
            //填充本地缓存
            cacheService.setCommonCache("item_" + id, itemModel);
        }

        ItemVO itemVO = convertFromModel(itemModel);

        return CommonReturnType.create(itemVO);
    }

    private ItemVO convertFromModel(ItemModel itemModel) {
        if (itemModel == null) {
            return null;
        }
        ItemVO itemVO = new ItemVO();
        BeanUtils.copyProperties(itemModel, itemVO);
        if (itemModel.getPromoModel() != null) {
            //有正在或即将进行的活动
            itemVO.setPromoStatus(itemModel.getPromoModel().getStatus());
            itemVO.setPromoId(itemModel.getPromoModel().getId());
            itemVO.setStartDate(itemModel.getPromoModel().getStartDate().toString(DateTimeFormat.forPattern("yyyy-MM" +
                    "-dd HH:mm:ss")));
            itemVO.setPromoPrice(itemModel.getPromoModel().getPromoItemPrice());
        } else {
            itemVO.setPromoStatus(0);
        }

        return itemVO;
    }

}
