package com.example.demo.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
