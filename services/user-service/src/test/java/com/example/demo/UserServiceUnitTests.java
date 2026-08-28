package com.example.demo;

import com.example.demo.auth.entity.User;
import com.example.demo.auth.mapper.UserMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.user.client.MerchantCatalogClient;
import com.example.demo.user.dto.UserProfileUpdateRequest;
import com.example.demo.user.mapper.AddressMapper;
import com.example.demo.user.mapper.CartMapper;
import com.example.demo.user.mapper.UserFavoriteMerchantMapper;
import com.example.demo.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTests {

    @Mock
    private UserMapper userMapper;
    @Mock
    private AddressMapper addressMapper;
    @Mock
    private CartMapper cartMapper;
    @Mock
    private UserFavoriteMerchantMapper favoriteMerchantMapper;
    @Mock
    private MerchantCatalogClient merchantCatalogClient;

    @InjectMocks
    private UserService userService;

    @Test
    void getProfileThrowsNotFoundWhenUserMissing() {
        when(userMapper.selectById(10001L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getProfile(10001L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(404);
                    assertThat(exception.getMessage()).isEqualTo("用户不存在");
                });
    }

    @Test
    void updateProfileRejectsPhoneUsedByAnotherUser() {
        User user = new User();
        user.setId(10001L);
        user.setUsername("demo");
        user.setPhone("13800138001");
        when(userMapper.selectById(10001L)).thenReturn(user);
        when(userMapper.selectCount(any())).thenReturn(1L);

        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setPhone("13800138099");

        assertThatThrownBy(() -> userService.updateProfile(10001L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).isEqualTo("手机号已被其他用户使用");
                });
    }
}
