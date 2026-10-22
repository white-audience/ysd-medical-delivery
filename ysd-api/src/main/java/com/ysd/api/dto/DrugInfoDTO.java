package com.ysd.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 药品信息快照：供 ai-service 按名称检索药品、获取用药相关信息使用。
 * 只暴露必要字段，避免跨服务直接传实体；字段与 Drug 实体的药品字段一一对应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrugInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    /** 0=OTC(非处方药)，1=处方药 */
    private Integer prescriptionType;

    private String manufacturer;

    private String approvalNumber;

    private String indications;

    private String contraindications;
}
