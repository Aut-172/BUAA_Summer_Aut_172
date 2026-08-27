package com.example.demo.merchant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品规格分组实体（如：规格、甜度、温度）
 * 对应数据库 spec_group 表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spec_group")
public class SpecGroup extends BaseEntity {

    /**
     * 所属商品ID
     */
    private Long productId;

    /**
     * 分组名称（如：规格、甜度、温度）
     */
    private String name;

    /**
     * 规格值(JSON数组, 如: ["大份","小份"])
     */
    @TableField("`values`")
    private String values;
}
