package com.ysd.controller.user;

import com.ysd.constant.StatusConstant;
import com.ysd.entity.Drug;
import com.ysd.result.Result;
import com.ysd.service.DrugService;
import com.ysd.vo.DrugVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userDrugController")
@RequestMapping("/user/drug")
@Slf4j
@Tag(name = "C端-药品浏览接口")
public class DrugController {
    @Autowired
    private DrugService drugService;

    /**
     * 根据分类id查询药品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @Operation(summary = "根据分类id查询药品")
    public Result<List<DrugVO>> list(Long categoryId) {
        Drug drug = new Drug();
        drug.setCategoryId(categoryId);
        drug.setStatus(StatusConstant.ENABLE);//查询在售的药品

        List<DrugVO> list = drugService.listWithSpecs(drug);

        return Result.success(list);
    }

}
