package com.example.demo.admin.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.admin.dto.AuditRiderRequest;
import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminRiderService {

    private final RiderMapper riderMapper;

    public PageResult<Rider> listRiders(int page, int pageSize, String keyword, String status) {
        LambdaQueryWrapper<Rider> wrapper = new LambdaQueryWrapper<Rider>()
                .orderByDesc(Rider::getCreateTime);

        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(Rider::getStatus, status.trim());
        }
        if (StrUtil.isNotBlank(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(Rider::getName, value)
                    .or()
                    .like(Rider::getPhone, value)
                    .or()
                    .like(Rider::getServiceArea, value));
        }

        Page<Rider> result = riderMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(pageSize, 1)), wrapper);
        return PageResult.of(result);
    }

    @Transactional
    public Rider auditRider(Long riderId, AuditRiderRequest request) {
        Rider rider = requireRider(riderId);
        String status = request == null ? null : request.getStatus();
        if (StrUtil.isBlank(status)) {
            throw BusinessException.badRequest("请选择审核状态");
        }
        String normalized = status.trim();
        if (!"pending".equals(normalized) && !"active".equals(normalized) && !"frozen".equals(normalized)) {
            throw BusinessException.badRequest("骑手状态不合法");
        }
        rider.setStatus(normalized);
        rider.setAuditOpinion(request.getOpinion());
        riderMapper.updateById(rider);
        return riderMapper.selectById(riderId);
    }

    @Transactional
    public Rider freezeRider(Long riderId) {
        Rider rider = requireRider(riderId);
        rider.setStatus("frozen");
        riderMapper.updateById(rider);
        return riderMapper.selectById(riderId);
    }

    @Transactional
    public Rider unfreezeRider(Long riderId) {
        Rider rider = requireRider(riderId);
        rider.setStatus("active");
        riderMapper.updateById(rider);
        return riderMapper.selectById(riderId);
    }

    private Rider requireRider(Long riderId) {
        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            throw BusinessException.notFound("骑手不存在");
        }
        return rider;
    }
}
