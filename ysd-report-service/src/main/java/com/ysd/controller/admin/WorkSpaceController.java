package com.ysd.controller.admin;

import com.ysd.result.Result;
import com.ysd.service.WorkspaceService;
import com.ysd.vo.BusinessDataVO;
import com.ysd.vo.DrugOverViewVO;
import com.ysd.vo.OrderOverViewVO;
import com.ysd.vo.DrugComboOverViewVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 工作台
 */
@RestController
@RequestMapping("/admin/workspace")
@Slf4j
@Tag(name = "工作台相关接口")
public class WorkSpaceController {

    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 工作台今日数据查询
     * @return
     */
    @GetMapping("/businessData")
    @Operation(summary = "工作台今日数据查询")
    public Result<BusinessDataVO> businessData(){
        //获得当天的开始时间
        LocalDateTime begin = LocalDateTime.now().with(LocalTime.MIN);
        //获得当天的结束时间
        LocalDateTime end = LocalDateTime.now().with(LocalTime.MAX);

        BusinessDataVO businessDataVO = workspaceService.getBusinessData(begin, end);
        return Result.success(businessDataVO);
    }

    /**
     * 查询订单管理数据
     * @return
     */
    @GetMapping("/overviewOrders")
    @Operation(summary = "查询订单管理数据")
    public Result<OrderOverViewVO> orderOverView(){
        return Result.success(workspaceService.getOrderOverView());
    }

    /**
     * 查询药品总览
     * @return
     */
    @GetMapping("/overviewDrugs")
    @Operation(summary = "查询药品总览")
    public Result<DrugOverViewVO> drugOverView(){
        return Result.success(workspaceService.getDrugOverView());
    }

    /**
     * 查询药品组合总览
     * @return
     */
    @GetMapping("/overviewDrugCombos")
    @Operation(summary = "查询药品组合总览")
    public Result<DrugComboOverViewVO> drugComboOverView(){
        return Result.success(workspaceService.getDrugComboOverView());
    }
}
