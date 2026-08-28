package com.example.demo.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.merchant.entity.MerchantStockChangeRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantStockChangeMapper extends BaseMapper<MerchantStockChangeRecord> {
}
