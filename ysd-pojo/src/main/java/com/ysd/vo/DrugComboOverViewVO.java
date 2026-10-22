package com.ysd.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 药品组合总览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugComboOverViewVO implements Serializable {
    // 在售数量
    private Integer sold;

    // 已停售数量
    private Integer discontinued;
}
