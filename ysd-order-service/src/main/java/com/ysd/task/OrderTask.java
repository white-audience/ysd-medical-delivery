package com.ysd.task;

import com.ysd.entity.Orders;
import com.ysd.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {


    @Autowired
    private OrderMapper orderMapper;
    /**
     * 处理支付超时订单
     */
    @Scheduled(cron = "0 * * * * ?")//每分钟执行一次
    @SchedulerLock(name = "order_processTimeoutOrder", lockAtLeastFor = "PT50S", lockAtMostFor = "PT5M")
    public void processTimeoutOrder() {
        log.info("定时处理支付超时订单,{}", LocalDateTime.now());

        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);

        List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(Orders.PENDING_PAYMENT, time);

        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("订单超时，自动取消");
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }
    }
/**
     * 处理处于待派送状态的订单
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @SchedulerLock(name = "order_processDeliveryOrder", lockAtLeastFor = "PT50S", lockAtMostFor = "PT5M")
    public void processDeliveryOrder(){
        log.info("处理处于待派送状态的订单,{}", LocalDateTime.now());

        LocalDateTime time = LocalDateTime.now().plusHours(-60);


        List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(Orders.DELIVERY_IN_PROGRESS, time);

        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.COMPLETED);
                orderMapper.update(orders);
            }
        }
    }
}
