package com.ysd.controller.user;

import com.ysd.constant.StatusConstant;
import com.ysd.entity.DrugCombo;
import com.ysd.result.Result;
import com.ysd.service.DrugComboService;
import com.ysd.vo.DrugItemVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userDrugComboController")
@RequestMapping("/user/drug-combo")
@Tag(name = "C端-药品组合浏览接口")
public class DrugComboController {
    @Autowired
    private DrugComboService drugComboService;

    /**
     * 条件查询
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @Operation(summary = "根据分类id查询药品组合")
    public Result<List<DrugCombo>> list(Long categoryId) {
        DrugCombo drugCombo = new DrugCombo();
        drugCombo.setCategoryId(categoryId);
        drugCombo.setStatus(StatusConstant.ENABLE);

        List<DrugCombo> list = drugComboService.list(drugCombo);
        return Result.success(list);
    }

    /**
     * 根据药品组合id查询包含的药品列表
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}/drugs")
    @Operation(summary = "根据药品组合id查询包含的药品列表")
    public Result<List<DrugItemVO>> drugList(@PathVariable("id") Long id) {
        List<DrugItemVO> list = drugComboService.getItemsByComboId(id);
        return Result.success(list);
    }
}
