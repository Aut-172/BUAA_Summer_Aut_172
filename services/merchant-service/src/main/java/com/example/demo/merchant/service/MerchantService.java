package com.example.demo.merchant.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.BusinessException;
import com.example.demo.merchant.dto.MerchantListDTO;
import com.example.demo.merchant.dto.MerchantProfileDTO;
import com.example.demo.merchant.dto.ProductDTO;
import com.example.demo.merchant.entity.Category;
import com.example.demo.merchant.entity.Product;
import com.example.demo.merchant.entity.ProductSpec;
import com.example.demo.merchant.entity.SpecGroup;
import com.example.demo.auth.entity.Merchant;
import com.example.demo.auth.mapper.MerchantMapper;
import com.example.demo.merchant.mapper.CategoryMapper;
import com.example.demo.merchant.mapper.ProductMapper;
import com.example.demo.merchant.mapper.ProductSpecMapper;
import com.example.demo.merchant.mapper.SpecGroupMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 商家服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantMapper merchantMapper;
    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final SpecGroupMapper specGroupMapper;
    private final ProductSpecMapper productSpecMapper;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\(\\+?(\\d+(\\.\\d+)?)元\\)");

    /**
     * 获取商家基本信息（不含商品列表）
     */
    public Merchant getMerchantBasicInfo(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(404, "商家不存在");
        }
        return merchant;
    }

    /**
     * 获取商家列表（分页）
     */
    public Page<Merchant> getMerchantList(String keyword, String category, Integer pageNum, Integer pageSize) {
        Page<Merchant> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getStatus, "active") // 只返回已审核通过的商家
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(Merchant::getName, keyword)
                        .or()
                        .like(Merchant::getDescription, keyword)
                        .or()
                        .like(Merchant::getTags, keyword))
                .eq(StrUtil.isNotBlank(category), Merchant::getCategory, category)
                .orderByDesc(Merchant::getMonthlySales);
        return merchantMapper.selectPage(page, wrapper);
    }

    /**
     * 获取商家列表（分页，含推荐商品）
     */
    public Page<MerchantListDTO> getMerchantListWithProducts(String keyword, String category, Integer pageNum, Integer pageSize) {
        Page<Merchant> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getStatus, "active")
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(Merchant::getName, keyword)
                        .or()
                        .like(Merchant::getDescription, keyword)
                        .or()
                        .like(Merchant::getTags, keyword))
                .eq(StrUtil.isNotBlank(category), Merchant::getCategory, category)
                .orderByDesc(Merchant::getMonthlySales);
        Page<Merchant> merchantPage = merchantMapper.selectPage(page, wrapper);

        // 转换为 DTO 并填充商品
        List<MerchantListDTO> dtoList = merchantPage.getRecords().stream()
                .map(this::convertToMerchantListDTO)
                .collect(Collectors.toList());

        Page<MerchantListDTO> resultPage = new Page<>(merchantPage.getCurrent(), merchantPage.getSize(), merchantPage.getTotal());
        resultPage.setRecords(dtoList);
        return resultPage;
    }

    /**
     * 将 Merchant 转换为 MerchantListDTO（含推荐商品）
     */
    private MerchantListDTO convertToMerchantListDTO(Merchant merchant) {
        MerchantListDTO dto = new MerchantListDTO();
        BeanUtil.copyProperties(merchant, dto);

        // 查询该商家的上架商品，取前3个
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getMerchantId, merchant.getId())
                        .eq(Product::getStatus, "active")
                        .last("LIMIT 3")
        );

        List<MerchantListDTO.ProductItem> productItems = products.stream().map(p -> {
            MerchantListDTO.ProductItem item = new MerchantListDTO.ProductItem();
            item.setId(p.getId());
            item.setName(p.getName());
            item.setImage(p.getImage());
            item.setPrice(p.getPrice());
            item.setMonthlySales(p.getMonthlySales());
            return item;
        }).collect(Collectors.toList());

        dto.setProducts(productItems);
        return dto;
    }

    /**
     * 获取商家详情（含商品分类和商品列表）
     */
    public MerchantProfileDTO getMerchantDetail(Long merchantId) {
        // 1. 查询商家信息
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(404, "商家不存在");
        }
        if (!"active".equals(merchant.getStatus())) {
            throw BusinessException.notFound("商家不存在或已下线");
        }

        // 2. 复制基本信息
        MerchantProfileDTO dto = new MerchantProfileDTO();
        BeanUtil.copyProperties(merchant, dto);

        // 3. 解析 tags（逗号分隔）
        if (StrUtil.isNotBlank(merchant.getTags())) {
            String[] tagArr = merchant.getTags().split(",");
            List<String> tagList = new ArrayList<>();
            for (String tag : tagArr) {
                if (StrUtil.isNotBlank(tag)) {
                    tagList.add(tag.trim());
                }
            }
            dto.setTags(tagList);
        } else {
            dto.setTags(Collections.emptyList());
        }

        // 4. 查询该商家的所有分类
        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder));

        // 5. 查询该商家的所有上架商品
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getMerchantId, merchantId)
                        .eq(Product::getStatus, "active"));

        if (products.isEmpty()) {
            dto.setCategoryList(Collections.emptyList());
            return dto;
        }

        // 6. 查询所有规格分组和规格值（按 product_id 关联）
        List<Long> productIds = products.stream().map(Product::getId).collect(Collectors.toList());
        List<SpecGroup> allSpecGroups = specGroupMapper.selectList(
                new LambdaQueryWrapper<SpecGroup>()
                        .in(SpecGroup::getProductId, productIds));
        List<ProductSpec> allSpecs = new ArrayList<>();
        if (!allSpecGroups.isEmpty()) {
            allSpecs = productSpecMapper.selectList(
                    new LambdaQueryWrapper<ProductSpec>()
                            .in(ProductSpec::getProductId, productIds));
        }

        // 按 productId 分组（spec_group）
        Map<Long, List<SpecGroup>> specGroupMap = allSpecGroups.stream()
                .collect(Collectors.groupingBy(SpecGroup::getProductId));
        // 按 productId 分组（product_spec）
        Map<Long, List<ProductSpec>> productSpecMap = allSpecs.stream()
                .collect(Collectors.groupingBy(ProductSpec::getProductId));

        // 7. 按分类组装数据
        Map<Long, List<Product>> productByCategory = products.stream()
                .collect(Collectors.groupingBy(Product::getCategoryId));

        List<MerchantProfileDTO.CategoryWithProducts> categoryList = new ArrayList<>();
        for (Category cat : categories) {
            List<Product> catProducts = productByCategory.get(cat.getId());
            if (catProducts == null || catProducts.isEmpty()) {
                continue;
            }

            MerchantProfileDTO.CategoryWithProducts catDTO = new MerchantProfileDTO.CategoryWithProducts();
            catDTO.setId(cat.getId());
            catDTO.setName(cat.getName());

            List<MerchantProfileDTO.ProductItem> productItems = new ArrayList<>();
            for (Product p : catProducts) {
                MerchantProfileDTO.ProductItem item = new MerchantProfileDTO.ProductItem();
                BeanUtil.copyProperties(p, item);

                // 组装规格（按 product_id 关联）
                List<SpecGroup> groupList = specGroupMap.getOrDefault(p.getId(), Collections.emptyList());
                List<ProductSpec> productSpecs = productSpecMap.getOrDefault(p.getId(), Collections.emptyList());
                List<MerchantProfileDTO.SpecGroupItem> specGroupItems = new ArrayList<>();
                for (SpecGroup sg : groupList) {
                    MerchantProfileDTO.SpecGroupItem sgi = new MerchantProfileDTO.SpecGroupItem();
                    sgi.setId(sg.getId());
                    sgi.setName(sg.getName());
                    // 解析 values JSON 数组为 SpecItem 列表
                    List<MerchantProfileDTO.SpecItem> specItems = new ArrayList<>();
                    if (StrUtil.isNotBlank(sg.getValues())) {
                        try {
                            List<String> valueList = OBJECT_MAPPER.readValue(sg.getValues(), new TypeReference<List<String>>() {});
                            for (int vi = 0; vi < valueList.size(); vi++) {
                                String val = valueList.get(vi);
                                MerchantProfileDTO.SpecItem si = new MerchantProfileDTO.SpecItem();
                                // 解析格式如 "大份(+3元)" 中的加价
                                String label = val;
                                BigDecimal extraPrice = BigDecimal.ZERO;
                                Matcher matcher = PRICE_PATTERN.matcher(val);
                                if (matcher.find()) {
                                    label = val.substring(0, matcher.start());
                                    extraPrice = new BigDecimal(matcher.group(1));
                                }
                                // 尝试从 product_spec 匹配
                                for (ProductSpec ps : productSpecs) {
                                    if (ps.getLabel().equals(label)) {
                                        si.setId(ps.getId());
                                        extraPrice = ps.getPrice();
                                        break;
                                    }
                                }
                                if (si.getId() == null) {
                                    si.setId((long) vi);
                                }
                                si.setName(label);
                                si.setExtraPrice(extraPrice);
                                specItems.add(si);
                            }
                        } catch (Exception e) {
                            log.warn("解析规格值JSON失败: {}", sg.getValues(), e);
                        }
                    }
                    sgi.setSpecs(specItems);
                    specGroupItems.add(sgi);
                }
                item.setSpecGroups(specGroupItems);
                productItems.add(item);
            }
            catDTO.setProducts(productItems);
            categoryList.add(catDTO);
        }
        dto.setCategoryList(categoryList);
        return dto;
    }

    /**
     * 获取商品详情
     */
    public ProductDTO getProductDetail(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }
        if (!"active".equals(product.getStatus())) {
            throw BusinessException.notFound("商品不存在或已下架");
        }
        requirePublicActiveMerchant(product.getMerchantId());

        ProductDTO dto = new ProductDTO();
        BeanUtil.copyProperties(product, dto);

        // 查询规格分组（按 product_id）
        List<SpecGroup> specGroups = specGroupMapper.selectList(
                new LambdaQueryWrapper<SpecGroup>()
                        .eq(SpecGroup::getProductId, productId));

        List<ProductSpec> allSpecs = productSpecMapper.selectList(
                new LambdaQueryWrapper<ProductSpec>()
                        .eq(ProductSpec::getProductId, productId));

        List<ProductDTO.SpecGroupItem> groupItems = new ArrayList<>();
        for (SpecGroup sg : specGroups) {
            ProductDTO.SpecGroupItem sgi = new ProductDTO.SpecGroupItem();
            sgi.setId(sg.getId());
            sgi.setName(sg.getName());

            // 解析 values JSON 数组
            List<ProductDTO.SpecItem> specItems = new ArrayList<>();
            if (StrUtil.isNotBlank(sg.getValues())) {
                try {
                    List<String> valueList = OBJECT_MAPPER.readValue(sg.getValues(), new TypeReference<List<String>>() {});
                    for (int vi = 0; vi < valueList.size(); vi++) {
                        String val = valueList.get(vi);
                        ProductDTO.SpecItem si = new ProductDTO.SpecItem();
                        String label = val;
                        BigDecimal extraPrice = BigDecimal.ZERO;
                        Matcher matcher = PRICE_PATTERN.matcher(val);
                        if (matcher.find()) {
                            label = val.substring(0, matcher.start());
                            extraPrice = new BigDecimal(matcher.group(1));
                        }
                        for (ProductSpec ps : allSpecs) {
                            if (ps.getLabel().equals(label)) {
                                si.setId(ps.getId());
                                extraPrice = ps.getPrice();
                                break;
                            }
                        }
                        if (si.getId() == null) {
                            si.setId((long) vi);
                        }
                        si.setName(label);
                        si.setExtraPrice(extraPrice);
                        specItems.add(si);
                    }
                } catch (Exception e) {
                    log.warn("解析规格值JSON失败: {}", sg.getValues(), e);
                }
            }
            sgi.setSpecs(specItems);
            groupItems.add(sgi);
        }
        dto.setSpecGroups(groupItems);
        return dto;
    }

    /**
     * 获取所有商品分类
     */
    public List<Category> getAllCategories() {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder));
    }

    /**
     * 更新商家信息（商家自己使用）
     */
    public Merchant updateMerchantProfile(Long merchantId, Merchant merchant) {
        Merchant existing = merchantMapper.selectById(merchantId);
        if (existing == null) {
            throw new BusinessException(404, "商家不存在");
        }
        merchant.setId(merchantId);
        // 不允许修改状态、评分等字段
        merchant.setStatus(null);
        merchant.setRating(null);
        merchant.setMonthlySales(null);
        merchantMapper.updateById(merchant);
        return merchantMapper.selectById(merchantId);
    }

    /**
     * 添加商品
     */
    public Product addProduct(Long merchantId, Product product) {
        requireActiveMerchant(merchantId);

        product.setId(null);
        product.setMerchantId(merchantId);
        product.setMonthlySales(0);
        if (product.getStatus() == null) {
            product.setStatus("active"); // 默认上架
        }
        if (product.getType() == null) {
            product.setType("normal");
        }
        productMapper.insert(product);
        return productMapper.selectById(product.getId());
    }

    /**
     * 更新商品
     */
    public Product updateProduct(Long merchantId, Product product) {
        requireActiveMerchant(merchantId);

        Product existing = productMapper.selectById(product.getId());
        if (existing == null) {
            throw new BusinessException(404, "商品不存在");
        }
        if (!existing.getMerchantId().equals(merchantId)) {
            throw new BusinessException(403, "无权操作该商品");
        }
        product.setMerchantId(null);
        product.setMonthlySales(null);
        productMapper.updateById(product);
        return productMapper.selectById(existing.getId());
    }

    /**
     * 删除商品
     */
    public void deleteProduct(Long merchantId, Long productId) {
        requireActiveMerchant(merchantId);

        Product existing = productMapper.selectById(productId);
        if (existing == null) {
            throw new BusinessException(404, "商品不存在");
        }
        if (!existing.getMerchantId().equals(merchantId)) {
            throw new BusinessException(403, "无权操作该商品");
        }
        productMapper.deleteById(productId);
    }

    /**
     * 获取商家的商品列表
     */
    public List<Product> getMerchantProducts(Long merchantId) {
        return productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getMerchantId, merchantId));
    }

    /**
     * 获取商家自己的商品详情
     */
    public Product getMerchantProduct(Long merchantId, Long productId) {
        requireActiveMerchant(merchantId);

        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }
        if (!merchantId.equals(product.getMerchantId())) {
            throw new BusinessException(403, "无权查看该商品");
        }
        return product;
    }

    /**
     * 添加规格分组
     */
    public void addSpecGroup(Long merchantId, SpecGroup specGroup) {
        requireActiveMerchant(merchantId);

        specGroup.setId(null);
        // spec_group 表使用 product_id，不再设置 merchantId
        specGroupMapper.insert(specGroup);
    }

    /**
     * 删除规格分组
     */
    public void deleteSpecGroup(Long merchantId, Long groupId) {
        requireActiveMerchant(merchantId);

        SpecGroup existing = specGroupMapper.selectById(groupId);
        if (existing == null) {
            throw new BusinessException(404, "规格分组不存在");
        }
        // 删除分组下的所有规格值（按 product_id）
        productSpecMapper.delete(new LambdaQueryWrapper<ProductSpec>()
                .eq(ProductSpec::getProductId, existing.getProductId()));
        specGroupMapper.deleteById(groupId);
    }

    /**
     * 添加规格值
     */
    public void addProductSpec(Long merchantId, ProductSpec productSpec) {
        requireActiveMerchant(merchantId);

        productSpec.setId(null);
        productSpecMapper.insert(productSpec);
    }

    /**
     * 删除规格值
     */
    public void deleteProductSpec(Long merchantId, Long specId) {
        requireActiveMerchant(merchantId);

        ProductSpec existing = productSpecMapper.selectById(specId);
        if (existing == null) {
            throw new BusinessException(404, "规格值不存在");
        }
        productSpecMapper.deleteById(specId);
    }

    private void requireActiveMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BusinessException(404, "商家不存在");
        }
        if (!"active".equals(merchant.getStatus())) {
            throw BusinessException.forbidden("商家账号审核通过后才能使用该功能");
        }
    }

    private void requirePublicActiveMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || !"active".equals(merchant.getStatus())) {
            throw BusinessException.notFound("商家不存在或已下线");
        }
    }
}
