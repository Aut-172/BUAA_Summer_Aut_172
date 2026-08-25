package com.example.demo.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    private String username;
    @JsonIgnore
    private String password;
    private String phone;
    private String nickname;
    private String avatar;
    private String role;    // consumer/merchant/rider/admin
    private String status;  // active/frozen
}
