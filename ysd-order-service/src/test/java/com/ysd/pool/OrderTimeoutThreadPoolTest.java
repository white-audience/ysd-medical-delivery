package com.ysd.pool;

import com.ysd.support.AbstractSpringBootTest;
import com.ysd.task.OrderTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证简历技术点 11：「线程池异步处理超时订单」。
 *
 * <p>勘察结论（见 {@code docs/resume-project-audit.md} 技术点 11）：
 * 全仓库<b>没有 {@code ThreadPoolExecutor} / {@code ThreadPoolTaskExecutor} / {@code @Async} /
 * {@code CompletableFuture}</b>，也没有任何线程池配置 Bean。
 * 超时订单是 {@code OrderTask.processTimeoutOrder} 里的<b>同步 for 循环</b>，
 * 跑在 Spring Task 默认的单线程调度器上（默认 pool size = 1，
 * 与每 5 秒一次的 {@code WebSocketTask} 争抢同一个线程）。
 *
 * <p>因此本类只有 POOL-00 是可执行的「现状断言」，POOL-01 ~ POOL-04 整体 {@code @Disabled}，
 * 它们描述的是技术点 11 实现之后应满足的验收标准。
 *
 * <p>参考的验收参数（简历若声称了具体数值，实现时应与此一致，否则同步修改本测试）：
 * corePoolSize=8、maxPoolSize=16、queueCapacity=100、keepAliveSeconds=60，
 * 拒绝策略需可观测（例如自定义 {@code RejectedExecutionHandler} 里用 {@code AtomicInteger} 计数）。
 */
@DisplayName("技术点11 线程池异步处理超时订单")
class OrderTimeoutThreadPoolTest extends AbstractSpringBootTest {

    @Test
    @DisplayName("POOL-00 【现状记录】OrderTask 不依赖任何线程池，超时订单处理是同步的")
    void 现状_超时订单处理未使用线程池() {
        List<String> fieldNames = Arrays.stream(OrderTask.class.getDeclaredFields())
                .map(Field::getName)
                .toList();
        List<Field> executorFields = Arrays.stream(OrderTask.class.getDeclaredFields())
                .filter(f -> Executor.class.isAssignableFrom(f.getType()))
                .toList();

        System.out.println("[POOL-00] OrderTask 的字段：" + fieldNames);
        System.out.println("[POOL-00] 仓库中 ThreadPoolExecutor / ThreadPoolTaskExecutor / @Async 出现次数：0");

        assertThat(executorFields)
                .as("OrderTask 中不存在 Executor 类型字段，说明超时订单是同步串行处理的（技术点11 未实现）")
                .isEmpty();
    }

    @Test
    @Disabled("技术点11 未实现：仓库中没有线程池 Bean，无法断言参数。"
            + "验收标准：corePoolSize=8、maxPoolSize=16、queueCapacity=100、keepAliveSeconds=60、线程名前缀可识别。"
            + "见 docs/resume-project-test-plan.md POOL-01")
    @DisplayName("【骨架·未实现】POOL-01 线程池核心参数应与配置一致")
    void 线程池核心参数_应与配置一致() {
        throw new UnsupportedOperationException("待技术点11 引入线程池后实现");
    }

    @Test
    @Disabled("技术点11 未实现：无队列、无拒绝策略，无法注入故障。"
            + "验收标准：提交「核心线程数 + 队列容量 + N」个阻塞任务后，AtomicInteger 计数到 N 次拒绝。"
            + "见 docs/resume-project-test-plan.md POOL-02")
    @DisplayName("【骨架·未实现】POOL-02 提交超过队列容量的任务时，拒绝策略应被触发")
    void 超过队列容量_应触发拒绝策略() {
        throw new UnsupportedOperationException("待技术点11 引入线程池后实现");
    }

    @Test
    @Disabled("技术点11 未实现：无异步执行链路，超时订单处理跑在调度线程上。"
            + "验收标准：处理任务的执行线程名不等于调度线程名。"
            + "见 docs/resume-project-test-plan.md POOL-03")
    @DisplayName("【骨架·未实现】POOL-03 超时订单处理应在非主线程异步执行")
    void 超时订单处理_应异步执行() {
        throw new UnsupportedOperationException("待技术点11 引入线程池后实现");
    }

    @Test
    @Disabled("技术点11 未实现：无线程池可关闭。"
            + "验收标准：shutdown() + awaitTermination(10s) 后 isTerminated() 为 true。"
            + "见 docs/resume-project-test-plan.md POOL-04")
    @DisplayName("【骨架·未实现】POOL-04 线程池关闭后应能正常终止全部任务")
    void 线程池_应能正常关闭() {
        throw new UnsupportedOperationException("待技术点11 引入线程池后实现");
    }
}
