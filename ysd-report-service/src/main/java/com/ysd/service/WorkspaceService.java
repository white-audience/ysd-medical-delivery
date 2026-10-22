package com.ysd.service;

import com.ysd.vo.BusinessDataVO;
import com.ysd.vo.DrugOverViewVO;
import com.ysd.vo.OrderOverViewVO;
import com.ysd.vo.DrugComboOverViewVO;
import java.time.LocalDateTime;

public interface WorkspaceService {

    /**
     * 根据时间段统计营业数据
     * @param begin
     * @param end
     * @return
     */
    BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end);

    /**
     * 查询订单管理数据
     * @return
     */
    OrderOverViewVO getOrderOverView();

    /**
     * 查询药品总览
     * @return
     */
    DrugOverViewVO getDrugOverView();

    /**
     * 查询药品组合总览
     * @return
     */
    DrugComboOverViewVO getDrugComboOverView();

}
