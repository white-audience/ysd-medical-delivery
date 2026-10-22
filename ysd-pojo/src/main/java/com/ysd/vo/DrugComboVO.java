package com.ysd.vo;

import com.ysd.entity.DrugComboItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugComboVO implements Serializable {

    private Long id;

    //分类id
    private Long categoryId;

    //药品组合名称
    private String name;

    //药品组合价格
    private BigDecimal price;

    //状态 0:停用 1:启用
    private Integer status;

    //描述信息
    private String description;

    //图片
    private String image;

    //更新时间
    private LocalDateTime updateTime;

    //分类名称
    private String categoryName;

    //药品组合明细
    private List<DrugComboItem> items = new ArrayList<>();
}
