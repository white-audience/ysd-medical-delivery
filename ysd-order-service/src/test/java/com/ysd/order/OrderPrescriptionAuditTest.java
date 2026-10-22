package com.ysd.order;

import com.ysd.api.client.AiPrescriptionClient;
import com.ysd.api.client.CatalogClient;
import com.ysd.api.client.UserClient;
import com.ysd.api.dto.AddressBookSnapshotDTO;
import com.ysd.api.dto.DrugInfoDTO;
import com.ysd.api.dto.PrescriptionCheckRequest;
import com.ysd.api.dto.PrescriptionCheckResponse;
import com.ysd.context.BaseContext;
import com.ysd.dto.OrdersSubmitDTO;
import com.ysd.entity.Orders;
import com.ysd.entity.ShoppingCart;
import com.ysd.exception.OrderBusinessException;
import com.ysd.mapper.OrderDetailMapper;
import com.ysd.mapper.OrderMapper;
import com.ysd.mapper.ShoppingCartMapper;
import com.ysd.mq.WsPushProducer;
import com.ysd.result.Result;
import com.ysd.service.impl.OrderServiceImpl;
import com.ysd.utils.WeChatPayUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 下单主流程的处方药拦截：
 * <ul>
 *   <li>OTC 直接放行，不调用 AI；</li>
 *   <li>含处方药但未上传处方，直接拦截；</li>
 *   <li>含处方药 + AI 审核通过，正常下单；</li>
 *   <li>AI 拒绝、超时、下游异常，一律 fail-closed。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("下单处方审核闭环")
class OrderPrescriptionAuditTest {

    private static final long USER_ID = 1001L;
    private static final long OTC_DRUG_ID = 10L;
    private static final long RX_DRUG_ID = 20L;
    private static final String RX_URL = "https://oss.example.com/rx/1001.jpg";

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderDetailMapper orderDetailMapper;
    @Mock
    private ShoppingCartMapper shoppingCartMapper;
    @Mock
    private UserClient userClient;
    @Mock
    private CatalogClient catalogClient;
    @Mock
    private AiPrescriptionClient aiPrescriptionClient;
    @Mock
    private WeChatPayUtil weChatPayUtil;
    @Mock
    private WsPushProducer wsPushProducer;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(USER_ID);
        when(userClient.getAddressBookById(anyLong()))
                .thenReturn(Result.success(new AddressBookSnapshotDTO("张三", "13800000000")));
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    @DisplayName("ORDER-01 购物车只有 OTC：正常下单，不调用 AI 审核")
    void otcOrder_passesWithoutAiAudit() {
        givenCart(OTC_DRUG_ID);
        when(catalogClient.getDrugInfoById(OTC_DRUG_ID))
                .thenReturn(Result.success(drug(OTC_DRUG_ID, "布洛芬缓释胶囊", 0)));

        orderService.submitOrder(submitDTO(null));

        verify(aiPrescriptionClient, never()).checkPrescription(any());
        ArgumentCaptor<Orders> orderCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(orderCaptor.capture());
        // 客户端省略 tablewareStatus 时按 DB 默认值 1 落库
        assertThat(orderCaptor.getValue().getTablewareStatus()).isEqualTo(1);
        assertThat(orderCaptor.getValue().getPackAmount()).isNull();
        verify(shoppingCartMapper).deleteByUserId(USER_ID);
    }

    @Test
    @DisplayName("ORDER-02 含处方药但未上传处方：直接拦截，不写订单")
    void prescriptionDrugWithoutUrl_rejectedBeforeDbWrite() {
        givenCart(RX_DRUG_ID);
        when(catalogClient.getDrugInfoById(RX_DRUG_ID))
                .thenReturn(Result.success(drug(RX_DRUG_ID, "阿莫西林胶囊", 1)));

        assertThatThrownBy(() -> orderService.submitOrder(submitDTO(null)))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessageContaining("请先上传处方");

        verify(aiPrescriptionClient, never()).checkPrescription(any());
        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("ORDER-03 含处方药 + AI 审核通过：正常下单，请求携带药品与处方")
    void prescriptionDrugWithApproval_passes() {
        givenCart(RX_DRUG_ID);
        when(catalogClient.getDrugInfoById(RX_DRUG_ID))
                .thenReturn(Result.success(drug(RX_DRUG_ID, "阿莫西林胶囊", 1)));
        when(aiPrescriptionClient.checkPrescription(any()))
                .thenReturn(Result.success(new PrescriptionCheckResponse(
                        true, "处方已上传，合规校验通过", List.of("阿莫西林胶囊"))));

        orderService.submitOrder(submitDTO(RX_URL));

        ArgumentCaptor<PrescriptionCheckRequest> captor =
                ArgumentCaptor.forClass(PrescriptionCheckRequest.class);
        verify(aiPrescriptionClient).checkPrescription(captor.capture());
        assertThat(captor.getValue().drugIds()).containsExactly(RX_DRUG_ID);
        assertThat(captor.getValue().prescriptionUrl()).isEqualTo(RX_URL);
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);

        verify(orderMapper).insert(any());
        verify(shoppingCartMapper).deleteByUserId(USER_ID);
    }

    @Test
    @DisplayName("ORDER-04 AI 返回审核不通过：拦截且不写订单")
    void aiRejected_orderBlocked() {
        givenCart(RX_DRUG_ID);
        when(catalogClient.getDrugInfoById(RX_DRUG_ID))
                .thenReturn(Result.success(drug(RX_DRUG_ID, "阿莫西林胶囊", 1)));
        when(aiPrescriptionClient.checkPrescription(any()))
                .thenReturn(Result.success(new PrescriptionCheckResponse(
                        false, "处方与药品不匹配", List.of())));

        assertThatThrownBy(() -> orderService.submitOrder(submitDTO(RX_URL)))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessage("处方与药品不匹配");

        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("ORDER-05 AI 超时/异常：fail-closed 拦截且不写订单")
    void aiTimeout_failClosed() {
        givenCart(RX_DRUG_ID);
        when(catalogClient.getDrugInfoById(RX_DRUG_ID))
                .thenReturn(Result.success(drug(RX_DRUG_ID, "阿莫西林胶囊", 1)));
        when(aiPrescriptionClient.checkPrescription(any()))
                .thenThrow(new RuntimeException("Read timed out"));

        assertThatThrownBy(() -> orderService.submitOrder(submitDTO(RX_URL)))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessageContaining("处方审核服务暂时不可用");

        verify(orderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("ORDER-06 catalog 查询失败：无法判断处方属性，fail-closed")
    void catalogFailure_failClosed() {
        givenCart(RX_DRUG_ID);
        when(catalogClient.getDrugInfoById(RX_DRUG_ID))
                .thenThrow(new RuntimeException("connect timeout"));

        assertThatThrownBy(() -> orderService.submitOrder(submitDTO(RX_URL)))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessageContaining("药品信息服务暂时不可用");

        verifyNoInteractions(aiPrescriptionClient);
        verify(orderMapper, never()).insert(any());
    }

    private void givenCart(Long drugId) {
        ShoppingCart cart = ShoppingCart.builder()
                .drugId(drugId)
                .name("测试药品")
                .number(1)
                .amount(new BigDecimal("19.90"))
                .build();
        when(shoppingCartMapper.list(any())).thenReturn(List.of(cart));
    }

    private OrdersSubmitDTO submitDTO(String prescriptionUrl) {
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(1L);
        dto.setAmount(new BigDecimal("19.90"));
        dto.setPrescriptionUrl(prescriptionUrl);
        // 故意省略 packAmount / tablewareNumber / tablewareStatus，覆盖可选字段缺省路径
        dto.setPayMethod(1);
        dto.setDeliveryStatus(1);
        return dto;
    }

    private DrugInfoDTO drug(Long id, String name, int prescriptionType) {
        return new DrugInfoDTO(id, name, prescriptionType, "厂家", "国药准字", "适应症", "禁忌");
    }
}
