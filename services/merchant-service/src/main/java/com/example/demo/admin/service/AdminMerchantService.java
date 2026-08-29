package com.example.demo.admin.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.admin.dto.AuditMerchantRequest;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMerchantService {

    private final MerchantMapper merchantMapper;

    public PageResult<Merchant> listMerchants(int page, int pageSize, String keyword, String status) {
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .orderByDesc(Merchant::getCreateTime);

        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(Merchant::getStatus, status.trim());
        }
        if (StrUtil.isNotBlank(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(Merchant::getUsername, value)
                    .or()
                    .like(Merchant::getName, value)
                    .or()
                    .like(Merchant::getPhone, value)
                    .or()
                    .like(Merchant::getAddress, value)
                    .or()
                    .like(Merchant::getCategory, value));
        }

        Page<Merchant> result = merchantMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(pageSize, 1)), wrapper);
        return PageResult.of(result);
    }

    @Transactional
    public Merchant auditMerchant(Long merchantId, AuditMerchantRequest request) {
        Merchant merchant = requireMerchant(merchantId);
        String status = request == null ? null : request.getStatus();
        if (StrUtil.isBlank(status)) {
            throw BusinessException.badRequest("请选择审核状态");
        }
        String normalized = status.trim();
        if (!"pending".equals(normalized) && !"active".equals(normalized) && !"frozen".equals(normalized) && !"rest".equals(normalized)) {
            throw BusinessException.badRequest("商家状态不合法");
        }
        merchant.setStatus(normalized);
        merchantMapper.updateById(merchant);
        return merchantMapper.selectById(merchantId);
    }

    @Transactional
    public Merchant freezeMerchant(Long merchantId) {
        Merchant merchant = requireMerchant(merchantId);
        merchant.setStatus("frozen");
        merchantMapper.updateById(merchant);
        return merchantMapper.selectById(merchantId);
    }

    @Transactional
    public Merchant unfreezeMerchant(Long merchantId) {
        Merchant merchant = requireMerchant(merchantId);
        merchant.setStatus("active");
        merchantMapper.updateById(merchant);
        return merchantMapper.selectById(merchantId);
    }

    private Merchant requireMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw BusinessException.notFound("商家不存在");
        }
        return merchant;
    }
}
