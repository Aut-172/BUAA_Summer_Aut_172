package com.example.demo.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.entity.User;
import com.example.demo.auth.mapper.UserMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.user.client.MerchantCatalogClient;
import com.example.demo.user.dto.*;
import com.example.demo.user.entity.Address;
import com.example.demo.user.entity.Cart;
import com.example.demo.user.entity.UserFavoriteMerchant;
import com.example.demo.user.mapper.AddressMapper;
import com.example.demo.user.mapper.CartMapper;
import com.example.demo.user.mapper.UserFavoriteMerchantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务
 * 提供个人资料、收货地址、购物车管理功能
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final AddressMapper addressMapper;
    private final CartMapper cartMapper;
    private final UserFavoriteMerchantMapper favoriteMerchantMapper;
    private final MerchantCatalogClient merchantCatalogClient;

    // ==================== 个人资料 ====================

    /**
     * 获取用户资料
     */
    public UserProfileVO getProfile(Long userId) {
        User user = requireActiveConsumer(userId);
        return UserProfileVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }

    public User getUserSnapshot(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }

    /**
     * 更新用户资料
     */
    public UserProfileVO updateProfile(Long userId, UserProfileUpdateRequest request) {
        User user = requireActiveConsumer(userId);

        // 更新非空字段
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getPhone() != null) {
            // 检查手机号是否已被其他用户使用
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, request.getPhone())
                    .ne(User::getId, userId);
            if (userMapper.selectCount(wrapper) > 0) {
                throw BusinessException.badRequest("手机号已被其他用户使用");
            }
            user.setPhone(request.getPhone());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }

        userMapper.updateById(user);
        return getProfile(userId);
    }

    // ==================== 收货地址 ====================

    /**
     * 获取用户地址列表
     */
    public List<AddressDTO> getAddressList(Long userId) {
        requireActiveConsumer(userId);
        List<Address> addresses = addressMapper.selectList(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .orderByDesc(Address::getIsDefault)
                        .orderByDesc(Address::getCreateTime)
        );
        return addresses.stream().map(this::toAddressDTO).collect(Collectors.toList());
    }

    /**
     * 获取单个地址
     */
    public AddressDTO getAddress(Long userId, Long addressId) {
        requireActiveConsumer(userId);
        Address address = addressMapper.selectOne(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getId, addressId)
                        .eq(Address::getUserId, userId)
        );
        if (address == null) {
            throw BusinessException.notFound("地址不存在");
        }
        return toAddressDTO(address);
    }

    /**
     * 新增地址
     */
    @Transactional
    public AddressDTO addAddress(Long userId, AddressDTO dto) {
        requireActiveConsumer(userId);
        // 如果设置为默认地址，先取消其他默认地址
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            clearDefaultAddress(userId);
        }

        Address address = new Address();
        address.setUserId(userId);
        address.setName(dto.getName());
        address.setPhone(dto.getPhone());
        address.setDetail(dto.getDetail());
        address.setLongitude(dto.getLongitude());
        address.setLatitude(dto.getLatitude());
        address.setIsDefault(dto.getIsDefault() != null && dto.getIsDefault());

        addressMapper.insert(address);
        return toAddressDTO(address);
    }

    /**
     * 更新地址
     */
    @Transactional
    public AddressDTO updateAddress(Long userId, Long addressId, AddressDTO dto) {
        requireActiveConsumer(userId);
        Address address = addressMapper.selectOne(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getId, addressId)
                        .eq(Address::getUserId, userId)
        );
        if (address == null) {
            throw BusinessException.notFound("地址不存在");
        }

        // 如果设置为默认地址，先取消其他默认地址
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            clearDefaultAddress(userId);
        }

        if (dto.getName() != null) address.setName(dto.getName());
        if (dto.getPhone() != null) address.setPhone(dto.getPhone());
        if (dto.getDetail() != null) address.setDetail(dto.getDetail());
        if (dto.getLongitude() != null) address.setLongitude(dto.getLongitude());
        if (dto.getLatitude() != null) address.setLatitude(dto.getLatitude());
        if (dto.getIsDefault() != null) address.setIsDefault(dto.getIsDefault());

        addressMapper.updateById(address);
        return toAddressDTO(address);
    }

    /**
     * 删除地址
     */
    public void deleteAddress(Long userId, Long addressId) {
        requireActiveConsumer(userId);
        int deleted = addressMapper.delete(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getId, addressId)
                        .eq(Address::getUserId, userId)
        );
        if (deleted == 0) {
            throw BusinessException.notFound("地址不存在");
        }
    }

    /**
     * 取消用户所有默认地址
     */
    private void clearDefaultAddress(Long userId) {
        List<Address> defaultAddresses = addressMapper.selectList(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .eq(Address::getIsDefault, true)
        );
        for (Address addr : defaultAddresses) {
            addr.setIsDefault(false);
            addressMapper.updateById(addr);
        }
    }

    // ==================== 购物车 ====================

    /**
     * 获取购物车列表
     */
    public List<CartVO> getCartList(Long userId) {
        requireActiveConsumer(userId);
        List<Cart> carts = cartMapper.selectList(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, userId)
                        .orderByDesc(Cart::getCreateTime)
        );
        return carts.stream().map(this::toCartVO).collect(Collectors.toList());
    }

    /**
     * 添加商品到购物车
     */
    @Transactional
    public CartVO addCart(Long userId, CartRequest request) {
        requireActiveConsumer(userId);
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            request.setQuantity(1);
        }

        // 检查是否已存在相同商品+规格
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getProductId, request.getProductId());
        if (request.getSpecLabel() != null && !request.getSpecLabel().isEmpty()) {
            wrapper.eq(Cart::getSpecLabel, request.getSpecLabel());
        } else {
            wrapper.isNull(Cart::getSpecLabel);
        }

        Cart existing = cartMapper.selectOne(wrapper);
        if (existing != null) {
            // 已存在则增加数量
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
            cartMapper.updateById(existing);
            return toCartVO(existing);
        }

        MerchantCatalogClient.ProductSnapshot productSnapshot = merchantCatalogClient.getProduct(request.getProductId());
        Long merchantId = request.getMerchantId() != null ? request.getMerchantId() : productSnapshot.getMerchantId();
        if (!merchantId.equals(productSnapshot.getMerchantId())) {
            throw BusinessException.badRequest("商品不属于当前商家");
        }

        MerchantCatalogClient.ProductQuote product = merchantCatalogClient.getProductQuote(
                merchantId, request.getProductId(), request.getSpecLabel(), request.getQuantity());
        if (product == null || product.getProductId() == null) {
            throw BusinessException.notFound("商品不存在");
        }
        if (!Boolean.TRUE.equals(product.getAvailable())) {
            throw BusinessException.badRequest(product.getMessage() != null ? product.getMessage() : "商品不存在或已下架");
        }

        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setMerchantId(merchantId);
        cart.setProductId(request.getProductId());
        cart.setName(product.getName());
        cart.setPrice(product.getPrice());
        cart.setImage(product.getImage());
        cart.setQuantity(request.getQuantity());
        cart.setSpecLabel(request.getSpecLabel());

        cartMapper.insert(cart);
        return toCartVO(cart);
    }

    /**
     * 更新购物车商品数量
     */
    public CartVO updateCartQuantity(Long userId, Long cartId, Integer quantity) {
        requireActiveConsumer(userId);
        Cart cart = cartMapper.selectOne(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getId, cartId)
                        .eq(Cart::getUserId, userId)
        );
        if (cart == null) {
            throw BusinessException.notFound("购物车记录不存在");
        }

        if (quantity <= 0) {
            // 数量为0或负数则删除
            cartMapper.deleteById(cartId);
            return null;
        }

        cart.setQuantity(quantity);
        cartMapper.updateById(cart);
        return toCartVO(cart);
    }

    /**
     * 删除购物车项
     */
    public void deleteCart(Long userId, Long cartId) {
        requireActiveConsumer(userId);
        int deleted = cartMapper.delete(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getId, cartId)
                        .eq(Cart::getUserId, userId)
        );
        if (deleted == 0) {
            throw BusinessException.notFound("购物车记录不存在");
        }
    }

    /**
     * 清空购物车
     */
    public void clearCart(Long userId) {
        requireActiveConsumer(userId);
        cartMapper.delete(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, userId)
        );
    }

    public void clearCartByMerchant(Long userId, Long merchantId) {
        cartMapper.delete(
                new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, userId)
                        .eq(Cart::getMerchantId, merchantId)
        );
    }

    // ==================== 收藏商家 ====================

    public List<FavoriteMerchantVO> getFavoriteMerchants(Long userId) {
        requireActiveConsumer(userId);
        List<UserFavoriteMerchant> favorites = favoriteMerchantMapper.selectList(
                new LambdaQueryWrapper<UserFavoriteMerchant>()
                        .eq(UserFavoriteMerchant::getUserId, userId)
                        .orderByDesc(UserFavoriteMerchant::getCreateTime)
        );

        return favorites.stream()
                .map(this::toFavoriteMerchantVO)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Boolean isFavoriteMerchant(Long userId, Long merchantId) {
        requireActiveConsumer(userId);
        return favoriteMerchantMapper.selectCount(
                new LambdaQueryWrapper<UserFavoriteMerchant>()
                        .eq(UserFavoriteMerchant::getUserId, userId)
                        .eq(UserFavoriteMerchant::getMerchantId, merchantId)
        ) > 0;
    }

    @Transactional
    public FavoriteMerchantVO addFavoriteMerchant(Long userId, Long merchantId) {
        requireActiveConsumer(userId);
        MerchantCatalogClient.MerchantSnapshot merchant = requireActiveMerchant(merchantId);
        UserFavoriteMerchant existing = favoriteMerchantMapper.selectOne(
                new LambdaQueryWrapper<UserFavoriteMerchant>()
                        .eq(UserFavoriteMerchant::getUserId, userId)
                        .eq(UserFavoriteMerchant::getMerchantId, merchantId)
                        .last("limit 1")
        );

        if (existing != null) {
            return toFavoriteMerchantVO(existing, merchant);
        }

        UserFavoriteMerchant favorite = new UserFavoriteMerchant();
        favorite.setUserId(userId);
        favorite.setMerchantId(merchantId);
        favoriteMerchantMapper.insert(favorite);
        return toFavoriteMerchantVO(favorite, merchant);
    }

    public void deleteFavoriteMerchant(Long userId, Long merchantId) {
        requireActiveConsumer(userId);
        favoriteMerchantMapper.delete(
                new LambdaQueryWrapper<UserFavoriteMerchant>()
                        .eq(UserFavoriteMerchant::getUserId, userId)
                        .eq(UserFavoriteMerchant::getMerchantId, merchantId)
        );
    }

    // ==================== 转换方法 ====================

    private AddressDTO toAddressDTO(Address address) {
        AddressDTO dto = new AddressDTO();
        dto.setId(address.getId());
        dto.setName(address.getName());
        dto.setPhone(address.getPhone());
        dto.setDetail(address.getDetail());
        dto.setLongitude(address.getLongitude());
        dto.setLatitude(address.getLatitude());
        dto.setIsDefault(address.getIsDefault());
        return dto;
    }

    private User requireActiveConsumer(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !"consumer".equals(user.getRole())) {
            throw BusinessException.notFound("用户不存在");
        }
        if ("frozen".equals(user.getStatus())) {
            throw BusinessException.forbidden("用户账号已被冻结，无法使用该功能");
        }
        return user;
    }

    private CartVO toCartVO(Cart cart) {
        String merchantName = "";
        if (cart.getMerchantId() != null) {
            MerchantCatalogClient.MerchantSnapshot merchant = merchantCatalogClient.getMerchant(cart.getMerchantId());
            if (merchant != null) {
                merchantName = merchant.getName();
            }
        }

        BigDecimal subtotal = cart.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity()));

        return CartVO.builder()
                .id(cart.getId())
                .merchantId(cart.getMerchantId())
                .merchantName(merchantName)
                .productId(cart.getProductId())
                .name(cart.getName())
                .price(cart.getPrice())
                .image(cart.getImage())
                .quantity(cart.getQuantity())
                .specLabel(cart.getSpecLabel())
                .subtotal(subtotal)
                .build();
    }

    private MerchantCatalogClient.MerchantSnapshot requireActiveMerchant(Long merchantId) {
        MerchantCatalogClient.MerchantSnapshot merchant = merchantCatalogClient.getMerchant(merchantId);
        if (merchant == null) {
            throw BusinessException.notFound("商家不存在");
        }
        if (!"active".equals(merchant.getStatus())) {
            throw BusinessException.badRequest("只能收藏正常营业商家");
        }
        return merchant;
    }

    private FavoriteMerchantVO toFavoriteMerchantVO(UserFavoriteMerchant favorite) {
        MerchantCatalogClient.MerchantSnapshot merchant = merchantCatalogClient.getMerchant(favorite.getMerchantId());
        if (merchant == null || !"active".equals(merchant.getStatus())) {
            return null;
        }
        return toFavoriteMerchantVO(favorite, merchant);
    }

    private FavoriteMerchantVO toFavoriteMerchantVO(UserFavoriteMerchant favorite,
                                                    MerchantCatalogClient.MerchantSnapshot merchant) {
        return FavoriteMerchantVO.builder()
                .favoriteId(favorite.getId())
                .merchantId(merchant.getId())
                .name(merchant.getName())
                .category(merchant.getCategory())
                .description(merchant.getDescription())
                .avatar(merchant.getAvatar())
                .tags(merchant.getTags())
                .rating(merchant.getRating())
                .monthlySales(merchant.getMonthlySales())
                .minDeliveryFee(merchant.getMinDeliveryFee())
                .deliveryFee(merchant.getDeliveryFee())
                .favoritedAt(favorite.getCreateTime())
                .build();
    }
}
