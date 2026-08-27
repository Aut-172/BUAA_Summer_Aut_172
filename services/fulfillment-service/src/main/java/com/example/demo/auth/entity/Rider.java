package com.example.demo.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 骑手实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rider")
public class Rider extends BaseEntity {

    private String name;
    @JsonIgnore
    private String password;
    private String phone;
    private String idCard;
    private String status;       // pending/active/frozen
    private String auditOpinion;
    private String serviceArea;
}
