package com.manster.seckill.dao;

import com.manster.seckill.entity.StockLogDO;
import org.springframework.stereotype.Repository;


@Repository
public interface StockLogDOMapper {
    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table stock_log
     *
     * @mbg.generated Wed May 18 13:23:53 CST 2022
     */
    int deleteByPrimaryKey(String stockLogId);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table stock_log
     *
     * @mbg.generated Wed May 18 13:23:53 CST 2022
     */
    int insert(StockLogDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table stock_log
     *
     * @mbg.generated Wed May 18 13:23:53 CST 2022
     */
    int insertSelective(StockLogDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table stock_log
     *
     * @mbg.generated Wed May 18 13:23:53 CST 2022
     */
    StockLogDO selectByPrimaryKey(String stockLogId);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table stock_log
     *
     * @mbg.generated Wed May 18 13:23:53 CST 2022
     */
    int updateByPrimaryKeySelective(StockLogDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table stock_log
     *
     * @mbg.generated Wed May 18 13:23:53 CST 2022
     */
    int updateByPrimaryKey(StockLogDO record);
}