package com.ysd.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugItemVO implements Serializable {

    //药品名称
    private String name;

    //数量
    private Integer quantity;

    //药品图片
    private String image;

    //药品描述
    private String description;
}
