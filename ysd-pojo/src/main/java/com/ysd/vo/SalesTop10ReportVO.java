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
public class SalesTop10ReportVO implements Serializable {

    //药品名称列表，以逗号分隔，例如：阿莫西林胶囊,布洛芬缓释胶囊,感冒灵颗粒
    private String nameList;

    //销量列表，以逗号分隔，例如：260,215,200
    private String numberList;

}
