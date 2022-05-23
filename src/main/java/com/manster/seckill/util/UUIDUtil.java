package com.manster.seckill.util;

import java.util.UUID;

/**
 * @author wxh
 * @date 2022-05-22 00:13
 */
public class UUIDUtil {

    public static String uuid() {
        ////替换“-”。主要是索引排序策略 加了-大家都一样 没有排序的意义了
        return UUID.randomUUID().toString().replace("-", "");
    }
}

