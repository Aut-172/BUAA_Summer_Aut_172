package com.example.demo.admin.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.admin.dto.AdminUserVO;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.mapper.UserMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;

    public PageResult<AdminUserVO> listUsers(int page, int pageSize, String keyword, String status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getRole, "consumer")
                .orderByDesc(User::getCreateTime);

        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(User::getStatus, status.trim());
        }
        if (StrUtil.isNotBlank(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, value)
                    .or()
                    .like(User::getNickname, value)
                    .or()
                    .like(User::getPhone, value));
        }

        Page<User> result = userMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(pageSize, 1)), wrapper);
        List<AdminUserVO> users = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(users, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public AdminUserVO freezeUser(Long userId) {
        User user = requireUser(userId);
        user.setStatus("frozen");
        userMapper.updateById(user);
        return toVO(userMapper.selectById(userId));
    }

    @Transactional
    public AdminUserVO unfreezeUser(Long userId) {
        User user = requireUser(userId);
        user.setStatus("active");
        userMapper.updateById(user);
        return toVO(userMapper.selectById(userId));
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !"consumer".equals(user.getRole())) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }

    private AdminUserVO toVO(User user) {
        return AdminUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .phone(user.getPhone())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .build();
    }
}
