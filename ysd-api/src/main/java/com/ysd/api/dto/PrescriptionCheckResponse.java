package com.ysd.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 处方审核结果。approved=false 时 order-service 必须拦截下单（fail-closed）。
 */
public record PrescriptionCheckResponse(boolean approved, String reason,
                                        List<String> prescriptionDrugs) implements Serializable {
}
