package com.example.demo.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.demo.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户收藏商家关系。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_favorite_merchant")
public class UserFavoriteMerchant extends BaseEntity {

    private Long userId;
    private Long merchantId;
}
