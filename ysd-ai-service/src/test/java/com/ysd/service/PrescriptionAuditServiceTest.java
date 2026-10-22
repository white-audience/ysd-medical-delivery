package com.ysd.service;

import com.ysd.api.client.CatalogClient;
import com.ysd.api.dto.DrugInfoDTO;
import com.ysd.api.dto.PrescriptionCheckRequest;
import com.ysd.api.dto.PrescriptionCheckResponse;
import com.ysd.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * AI 内部处方审核的确定性规则与失败路径。
 * 任何下游异常都必须 fail-closed（approved=false）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AI 内部处方审核")
class PrescriptionAuditServiceTest {

    @Mock
    private CatalogClient catalogClient;

    @InjectMocks
    private PrescriptionAuditService service;

    @Test
    @DisplayName("AUDIT-01 纯 OTC：放行且无需处方")
    void otc_approvedWithoutPrescription() {
        when(catalogClient.getDrugInfoById(10L))
                .thenReturn(Result.success(drug(10L, "布洛芬缓释胶囊", 0)));

        PrescriptionCheckResponse response = service.check(
                new PrescriptionCheckRequest(List.of(10L), null, 1L));

        assertThat(response.approved()).isTrue();
        assertThat(response.reason()).contains("不含处方药");
        assertThat(response.prescriptionDrugs()).isEmpty();
    }

    @Test
    @DisplayName("AUDIT-02 含处方药但未上传处方：拒绝")
    void prescriptionDrug_withoutUrl_rejected() {
        when(catalogClient.getDrugInfoById(20L))
                .thenReturn(Result.success(drug(20L, "阿莫西林胶囊", 1)));

        PrescriptionCheckResponse response = service.check(
                new PrescriptionCheckRequest(List.of(20L), "  ", 1L));

        assertThat(response.approved()).isFalse();
        assertThat(response.reason())
                .contains("请先上传处方")
                .contains("阿莫西林胶囊");
    }

    @Test
    @DisplayName("AUDIT-03 含处方药且已上传处方：放行并回传药品名")
    void prescriptionDrug_withUrl_approved() {
        when(catalogClient.getDrugInfoById(20L))
                .thenReturn(Result.success(drug(20L, "阿莫西林胶囊", 1)));

        PrescriptionCheckResponse response = service.check(
                new PrescriptionCheckRequest(List.of(20L), "https://oss.example.com/rx/1.jpg", 1L));

        assertThat(response.approved()).isTrue();
        assertThat(response.prescriptionDrugs()).containsExactly("阿莫西林胶囊");
        assertThat(response.reason()).contains("待药师复核");
    }

    @Test
    @DisplayName("AUDIT-04 下游药品服务异常：fail-closed 拒绝")
    void catalogException_rejected() {
        when(catalogClient.getDrugInfoById(20L))
                .thenThrow(new RuntimeException("Read timed out"));

        PrescriptionCheckResponse response = service.check(
                new PrescriptionCheckRequest(List.of(20L), "https://oss.example.com/rx/1.jpg", 1L));

        assertThat(response.approved()).isFalse();
        assertThat(response.reason()).contains("不可用");
    }

    @Test
    @DisplayName("AUDIT-05 药品不存在：fail-closed 拒绝")
    void drugNotFound_rejected() {
        when(catalogClient.getDrugInfoById(99L)).thenReturn(Result.success(null));

        PrescriptionCheckResponse response = service.check(
                new PrescriptionCheckRequest(List.of(99L), "https://oss.example.com/rx/1.jpg", 1L));

        assertThat(response.approved()).isFalse();
        assertThat(response.reason()).contains("查询失败");
    }

    private DrugInfoDTO drug(Long id, String name, int prescriptionType) {
        return new DrugInfoDTO(id, name, prescriptionType, "厂家", "国药准字", "适应症", "禁忌");
    }
}
