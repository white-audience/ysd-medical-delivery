package com.ysd.order;

import com.ysd.entity.Orders;
import com.ysd.mapper.OrderMapper;
import com.ysd.task.OrderTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证简历技术点 12：「Spring Task 定时扫描超时订单」。
 *
 * <p>被测方法：{@code OrderTask.processTimeoutOrder()}，cron = {@code 0 * * * * ?}（每分钟一次），
 * 把「状态=待付款(1) 且 下单时间 < now-15min」的订单改为「已取消(6)」。
 *
 * <p>定时任务没有分布式锁，多实例部署会重复执行；本类用 {@code @Disabled} 的用例
 * （TASK-09）标出该验收标准，用反射用例（TASK-08）记录实际 cron 配置。
 * 详见 {@code docs/bugs-found.md} BUG-10。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("技术点12 Spring Task 超时订单扫描")
class OrderTaskTest {

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderTask orderTask;

    private Orders overdueOrder(long id) {
        return Orders.builder()
                .id(id)
                .number("N-" + id)
                .status(Orders.PENDING_PAYMENT)
                .orderTime(LocalDateTime.now().minusMinutes(20))
                .build();
    }

    // ------------------------------------------------------------------ 核心行为

    @Test
    @DisplayName("TASK-01 超时待付款订单被置为已取消，并写入取消原因与取消时间")
    void processTimeoutOrder_超时订单_被取消() {
        when(orderMapper.getByStatusAndOrderTime(eq(Orders.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of(overdueOrder(1L)));

        orderTask.processTimeoutOrder();

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).update(captor.capture());
        Orders updated = captor.getValue();

        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getStatus()).isEqualTo(Orders.CANCELLED);
        assertThat(updated.getCancelReason()).isEqualTo("订单超时，自动取消");
        assertThat(updated.getCancelTime()).as("取消时间必须写入").isNotNull();
    }

    @Test
    @DisplayName("TASK-02 连续调用两次具备幂等性：第二次扫描不再重复处理")
    void processTimeoutOrder_连续两次_幂等() {
        // 用 thenAnswer 模拟数据库真实行为：订单被改成已取消后，就不再属于「待付款超时」集合
        List<Orders> storage = new java.util.ArrayList<>();
        storage.add(overdueOrder(1L));

        when(orderMapper.getByStatusAndOrderTime(eq(Orders.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenAnswer(invocation -> storage.stream()
                        .filter(o -> Objects.equals(o.getStatus(), Orders.PENDING_PAYMENT))
                        .collect(Collectors.toList()));

        orderTask.processTimeoutOrder();
        orderTask.processTimeoutOrder();

        verify(orderMapper, times(1)).update(any(Orders.class));
        assertThat(storage.get(0).getStatus())
                .as("第二次执行后订单状态仍为已取消，没有被改成其他状态")
                .isEqualTo(Orders.CANCELLED);
    }

    @Test
    @DisplayName("TASK-06 多笔超时订单全部被处理")
    void processTimeoutOrder_多笔订单_全部处理() {
        when(orderMapper.getByStatusAndOrderTime(eq(Orders.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of(overdueOrder(1L), overdueOrder(2L), overdueOrder(3L)));

        orderTask.processTimeoutOrder();

        verify(orderMapper, times(3)).update(any(Orders.class));
    }

    @Test
    @DisplayName("TASK-03 非超时订单不受影响：扫描阈值约为 now-15min，且不发生任何更新")
    void processTimeoutOrder_非超时订单_状态不变() {
        when(orderMapper.getByStatusAndOrderTime(eq(Orders.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusMinutes(15);
        orderTask.processTimeoutOrder();
        LocalDateTime after = LocalDateTime.now().minusMinutes(15);

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderMapper).getByStatusAndOrderTime(eq(Orders.PENDING_PAYMENT), threshold.capture());
        assertThat(threshold.getValue())
                .as("超时阈值应为 now-15 分钟")
                .isBetween(before.minusSeconds(5), after.plusSeconds(5));

        verify(orderMapper, never()).update(any(Orders.class));
    }

    // ------------------------------------------------------------------ 边界条件

    @Test
    @DisplayName("TASK-04 扫描结果为空集合时不抛异常且不做更新")
    void processTimeoutOrder_空集合_安全返回() {
        when(orderMapper.getByStatusAndOrderTime(eq(Orders.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertThatCode(() -> orderTask.processTimeoutOrder()).doesNotThrowAnyException();
        verify(orderMapper, never()).update(any(Orders.class));
    }

    @Test
    @DisplayName("TASK-05 扫描结果为 null 时不抛 NPE")
    void processTimeoutOrder_结果null_安全返回() {
        when(orderMapper.getByStatusAndOrderTime(eq(Orders.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(null);

        assertThatCode(() -> orderTask.processTimeoutOrder()).doesNotThrowAnyException();
        verify(orderMapper, never()).update(any(Orders.class));
    }

    @Test
    @DisplayName("TASK-07 超 60 小时仍在派送中的订单被置为已完成")
    void processDeliveryOrder_超时派送订单_置为已完成() {
        Orders delivering = Orders.builder()
                .id(9L)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .orderTime(LocalDateTime.now().minusHours(61))
                .build();
        when(orderMapper.getByStatusAndOrderTime(eq(Orders.DELIVERY_IN_PROGRESS), any(LocalDateTime.class)))
                .thenReturn(List.of(delivering));

        orderTask.processDeliveryOrder();

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).update(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(Orders.COMPLETED);
    }

    // ------------------------------------------------------------------ 配置与缺口

    @Test
    @DisplayName("TASK-08 任务方法上存在 @Scheduled 注解且 cron 非空（记录实际配置）")
    void processTimeoutOrder_存在Scheduled注解() throws Exception {
        Method method = OrderTask.class.getMethod("processTimeoutOrder");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).as("processTimeoutOrder 应由 Spring Task 调度").isNotNull();
        assertThat(scheduled.cron()).as("当前 cron 配置").isEqualTo("0 * * * * ?");
    }

    @Test
    @Disabled("技术点12 未实现分布式锁：仓库中无 Redisson / ShedLock / 任何锁代码，"
            + "多实例部署时每个实例都会重复执行超时扫描，真实退款场景会重复退款。见 docs/resume-project-audit.md 技术点12")
    @DisplayName("【骨架·未实现】TASK-09 定时任务应具备分布式锁，保证多实例下单次执行")
    void processTimeoutOrder_应具备分布式锁() {
        throw new UnsupportedOperationException("待技术点12 补齐分布式锁后实现");
    }
}
