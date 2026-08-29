package com.example.demo;

import com.example.demo.auth.entity.Rider;
import com.example.demo.auth.mapper.RiderMapper;
import com.example.demo.common.BusinessException;
import com.example.demo.fulfillment.client.MerchantCatalogClient;
import com.example.demo.fulfillment.client.OrderClient;
import com.example.demo.rider.dto.RiderTaskUpdateRequest;
import com.example.demo.rider.service.RiderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiderServiceUnitTests {

    @Mock
    private RiderMapper riderMapper;
    @Mock
    private OrderClient orderClient;
    @Mock
    private MerchantCatalogClient merchantCatalogClient;

    @InjectMocks
    private RiderService riderService;

    @Test
    void frozenRiderCannotAcceptTaskAndDoesNotCallOrderService() {
        Rider rider = rider(40002L, "frozen");
        when(riderMapper.selectById(40002L)).thenReturn(rider);

        RiderTaskUpdateRequest request = new RiderTaskUpdateRequest();
        request.setStatus("待接单");

        assertThatThrownBy(() -> riderService.updateTask(40002L, 70001L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(403);
                    assertThat(exception.getMessage()).isEqualTo("骑手账号审核通过后才能使用该功能");
                });
        verify(orderClient, never()).assignRider(70001L, 40002L);
    }

    @Test
    void activeRiderRejectsUnsupportedTaskStatus() {
        when(riderMapper.selectById(40001L)).thenReturn(rider(40001L, "active"));

        RiderTaskUpdateRequest request = new RiderTaskUpdateRequest();
        request.setStatus("未知状态");

        assertThatThrownBy(() -> riderService.updateTask(40001L, 70001L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).isEqualTo("非法的任务状态: 未知状态");
                });
    }

    private Rider rider(Long id, String status) {
        Rider rider = new Rider();
        rider.setId(id);
        rider.setUsername("rider" + id);
        rider.setName("rider" + id);
        rider.setPhone("13800138004");
        rider.setStatus(status);
        return rider;
    }
}
