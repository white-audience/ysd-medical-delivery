package com.ysd.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 药品组合明细
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugComboItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //药品组合id
    private Long comboId;

    //药品id
    private Long drugId;

    //药品名称 （冗余字段）
    private String name;

    //药品原价
    private BigDecimal price;

    //数量
    private Integer quantity;
}
