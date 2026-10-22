package com.ysd.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class ShoppingCartDTO implements Serializable {

    private Long drugId;
    private Long comboId;
    private String spec;

}
