package com.ysd.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 下单前处方审核请求：order-service 将购物车中的药品 ID、处方凭证和用户 ID 交给 ai-service。
 * prescriptionUrl 是处方影像/电子处方的存储地址，仅用于本次审核，不落订单表。
 */
public record PrescriptionCheckRequest(List<Long> drugIds, String prescriptionUrl,
                                      Long userId) implements Serializable {
}
