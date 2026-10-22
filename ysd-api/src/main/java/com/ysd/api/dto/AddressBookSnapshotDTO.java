package com.ysd.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 地址簿快照：order-service 下单时只需要收货人 + 电话。
 * 只暴露必要字段，避免跨服务直接传实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressBookSnapshotDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 收货人 */
    private String consignee;

    /** 手机号 */
    private String phone;
}
