package com.ysd.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药品
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Drug implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //药品名称
    private String name;

    //药品分类id
    private Long categoryId;

    //药品价格
    private BigDecimal price;

    //图片
    private String image;

    //描述信息
    private String description;

    //0 停售 1 在售
    private Integer status;

    //处方药类型：0 OTC非处方药  1 处方药
    @Builder.Default
    private Integer prescriptionType = 0;
    //生产厂家
    private String manufacturer;
    //国药准字/批准文号
    private String approvalNumber;
    //适应症/功能主治
    private String indications;
    //禁忌
    private String contraindications;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createUser;

    private Long updateUser;

}
