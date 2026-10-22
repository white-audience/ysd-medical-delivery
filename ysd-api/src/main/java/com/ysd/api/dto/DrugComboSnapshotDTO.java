package com.ysd.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 药品组合快照：order-service 加购物车时只需要 name / image / price。
 * 只暴露必要字段，避免跨服务直接传实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrugComboSnapshotDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    private String image;

    private BigDecimal price;
}
