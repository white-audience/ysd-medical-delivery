package com.ysd.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 药品规格
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    //药品id
    private Long drugId;

    //规格名称
    private String name;

    //规格数据list
    private String value;

}
