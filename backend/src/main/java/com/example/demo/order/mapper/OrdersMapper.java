package com.example.demo.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.order.entity.Orders;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrdersMapper extends BaseMapper<Orders> {
}
