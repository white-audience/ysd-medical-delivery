package com.ysd.order;

import com.ysd.api.client.UserClient;
import com.ysd.context.BaseContext;
import com.ysd.dto.OrdersCancelDTO;
import com.ysd.dto.OrdersConfirmDTO;
import com.ysd.dto.OrdersPaymentDTO;
import com.ysd.dto.OrdersRejectionDTO;
import com.ysd.entity.Orders;
import com.ysd.exception.OrderBusinessException;
import com.ysd.mapper.OrderDetailMapper;
import com.ysd.mapper.OrderMapper;
import com.ysd.mapper.ShoppingCartMapper;
import com.ysd.service.impl.OrderServiceImpl;
import com.ysd.utils.WeChatPayUtil;
import com.ysd.mq.WsPushProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证简历技术点 3：「订单状态流转」。
 *
 * <p>订单状态机（{@code Orders} 常量定义）：
 * <pre>
 *   1 待付款 → 2 待接单 → 3 已接单 → 4 派送中 → 5 已完成
 *                                        ↘ 6 已取消
 * </pre>
 *
 * <p>本类覆盖两类断言：
 * <ol>
 *   <li>合法流转能被正确写库（含 {@code cancelReason} / {@code deliveryTime} 等伴随字段）；</li>
 *   <li>非法流转必须抛 {@link OrderBusinessException}。</li>
 * </ol>
 *
 * <p>标注 {@code @Tag("bug-evidence")} 的用例是**预期失败**的：它们揭示了
 * {@code confirm} / {@code cancel} 缺少前置状态校验（BUG-4）、
 * {@code payment} 不校验重复支付（BUG-5），详见 {@code docs/bugs-found.md}。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("技术点3 订单状态流转")
class OrderStateMachineTest {

    private static final long ORDER_ID = 100L;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderDetailMapper orderDetailMapper;
    @Mock
    private ShoppingCartMapper shoppingCartMapper;
    @Mock
    private UserClient userClient;
    @Mock
    private WeChatPayUtil weChatPayUtil;
    @Mock
    private WsPushProducer wsPushProducer;

    @InjectMocks
    private OrderServiceImpl orderService;

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    private void givenOrderStatus(Integer status) {
        when(orderMapper.getById(ORDER_ID))
                .thenReturn(Orders.builder().id(ORDER_ID).number("N-100").status(status).payStatus(Orders.UN_PAID).build());
    }

    private Orders capturedUpdate() {
        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).update(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------ 合法流转

    @Test
    @DisplayName("ORD-02 待接单(2) → 已接单(3)：confirm 成功")
    void confirm_待接单到已接单_成功() {
        givenOrderStatus(Orders.TO_BE_CONFIRMED);

        OrdersConfirmDTO dto = new OrdersConfirmDTO();
        dto.setId(ORDER_ID);
        dto.setStatus(Orders.CONFIRMED);
        orderService.confirm(dto);

        Orders updated = capturedUpdate();
        assertThat(updated.getId()).isEqualTo(ORDER_ID);
        assertThat(updated.getStatus()).isEqualTo(Orders.CONFIRMED);
    }

    @Test
    @DisplayName("ORD-03 已接单(3) → 派送中(4)：delivery 成功")
    void delivery_已接单到派送中_成功() {
        givenOrderStatus(Orders.CONFIRMED);

        orderService.delivery(ORDER_ID);

        assertThat(capturedUpdate().getStatus()).isEqualTo(Orders.DELIVERY_IN_PROGRESS);
    }

    @Test
    @DisplayName("ORD-04 派送中(4) → 已完成(5)：complete 成功并写入送达时间")
    void complete_派送中到已完成_成功并写入送达时间() {
        givenOrderStatus(Orders.DELIVERY_IN_PROGRESS);

        orderService.complete(ORDER_ID);

        Orders updated = capturedUpdate();
        assertThat(updated.getStatus()).isEqualTo(Orders.COMPLETED);
        assertThat(updated.getDeliveryTime()).as("完成订单必须记录送达时间").isNotNull();
    }

    @Test
    @DisplayName("ORD-08 待接单(2) → 已取消(6)：商家 cancel 成功并写入取消原因")
    void cancel_待接单到已取消_成功() throws Exception {
        givenOrderStatus(Orders.TO_BE_CONFIRMED);

        OrdersCancelDTO dto = new OrdersCancelDTO();
        dto.setId(ORDER_ID);
        dto.setCancelReason("备货不足");

        orderService.cancel(dto);

        Orders updated = capturedUpdate();
        assertThat(updated.getStatus()).isEqualTo(Orders.CANCELLED);
        assertThat(updated.getCancelReason()).isEqualTo("备货不足");
        assertThat(updated.getCancelTime()).isNotNull();
    }

    @Test
    @DisplayName("ORD-08b 待接单(2) → 已取消(6)：商家拒单成功")
    void rejection_待接单到已取消_成功() throws Exception {
        givenOrderStatus(Orders.TO_BE_CONFIRMED);

        OrdersRejectionDTO dto = new OrdersRejectionDTO();
        dto.setId(ORDER_ID);
        dto.setRejectionReason("药品售罄");

        orderService.rejection(dto);

        Orders updated = capturedUpdate();
        assertThat(updated.getStatus()).isEqualTo(Orders.CANCELLED);
        assertThat(updated.getRejectionReason()).isEqualTo("药品售罄");
    }

    // ------------------------------------------------------------------ 非法流转

    @Test
    @DisplayName("ORD-05 已完成(5) → 派送中(4) 属于非法流转，应抛业务异常")
    void delivery_已完成订单_抛业务异常() {
        givenOrderStatus(Orders.COMPLETED);

        assertThatThrownBy(() -> orderService.delivery(ORDER_ID))
                .isInstanceOf(OrderBusinessException.class);

        verify(orderMapper, never()).update(any(Orders.class));
    }

    @Test
    @DisplayName("ORD-06 已完成(5) → 已完成(5) 属于非法流转，应抛业务异常")
    void complete_已完成订单_抛业务异常() {
        givenOrderStatus(Orders.COMPLETED);

        assertThatThrownBy(() -> orderService.complete(ORDER_ID))
                .isInstanceOf(OrderBusinessException.class);

        verify(orderMapper, never()).update(any(Orders.class));
    }

    @Test
    @DisplayName("ORD-07 已完成(5) → 已取消(6) 属于非法流转，应抛业务异常")
    void userCancelById_已完成订单_抛业务异常() {
        givenOrderStatus(Orders.COMPLETED);

        assertThatThrownBy(() -> orderService.userCancelById(ORDER_ID))
                .isInstanceOf(OrderBusinessException.class);

        verify(orderMapper, never()).update(any(Orders.class));
    }

    @Test
    @DisplayName("ORD-12 订单不存在时派送应抛业务异常")
    void delivery_订单不存在_抛业务异常() {
        when(orderMapper.getById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> orderService.delivery(ORDER_ID))
                .isInstanceOf(OrderBusinessException.class);

        verify(orderMapper, never()).update(any(Orders.class));
    }

    @Test
    @DisplayName("ORD-12b 非待接单状态不能拒单")
    void rejection_非待接单状态_抛业务异常() {
        givenOrderStatus(Orders.CONFIRMED);

        OrdersRejectionDTO dto = new OrdersRejectionDTO();
        dto.setId(ORDER_ID);
        dto.setRejectionReason("不做了");

        assertThatThrownBy(() -> orderService.rejection(dto))
                .isInstanceOf(OrderBusinessException.class);

        verify(orderMapper, never()).update(any(Orders.class));
    }

    // ------------------------------------------------------- 预期失败：暴露状态机缺口

    @Test
    @Tag("bug-evidence")
    @DisplayName("【预期失败·BUG-4】ORD-09 已完成(5) → 已接单(3) 应被拒绝，但 confirm 无前置校验")
    void confirm_已完成订单_应抛业务异常() {
        givenOrderStatus(Orders.COMPLETED);

        OrdersConfirmDTO dto = new OrdersConfirmDTO();
        dto.setId(ORDER_ID);
        dto.setStatus(Orders.CONFIRMED);

        assertThatThrownBy(() -> orderService.confirm(dto))
                .as("confirm 未校验前置状态，已完成的订单可以被改回「已接单」，订单状态机可被非法回退（BUG-4）")
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    @Tag("bug-evidence")
    @DisplayName("【预期失败·BUG-4】ORD-10 已完成(5) → 已取消(6) 应被拒绝，但 cancel 无前置校验")
    void cancel_已完成订单_应抛业务异常() {
        givenOrderStatus(Orders.COMPLETED);

        OrdersCancelDTO dto = new OrdersCancelDTO();
        dto.setId(ORDER_ID);
        dto.setCancelReason("手滑取消");

        assertThatThrownBy(() -> orderService.cancel(dto))
                .as("cancel 未校验前置状态，已完成的订单可以被改回「已取消」（BUG-4）")
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    @Tag("bug-evidence")
    @DisplayName("【预期失败·BUG-5】ORD-11 已支付订单重复支付应被拒绝，但 payment 完全不查订单状态")
    void payment_重复支付_应被拒绝() {
        BaseContext.setCurrentId(1L);

        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber("N-100");
        dto.setPayMethod(1);

        assertThatThrownBy(() -> orderService.payment(dto))
                .as("payment 直接更新支付状态，从不查询订单当前状态，重复支付不会被拦截（BUG-5）")
                .isInstanceOf(OrderBusinessException.class);
    }
}
