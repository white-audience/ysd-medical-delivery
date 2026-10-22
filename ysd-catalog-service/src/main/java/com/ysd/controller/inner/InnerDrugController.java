package com.ysd.controller.inner;

import com.ysd.api.dto.DrugSnapshotDTO;
import com.ysd.api.dto.DrugInfoDTO;
import com.ysd.entity.Drug;
import com.ysd.result.Result;
import com.ysd.service.DrugService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 内网接口：供 order-service 通过 Feign 调用（加购物车时取 name / image / price）。
 * 无登录校验，仅由网关屏蔽外部访问。
 */
@RestController
@RequestMapping("/inner/drug")
public class InnerDrugController {

    @Autowired
    private DrugService drugService;

    @GetMapping("/{id}")
    public Result<DrugSnapshotDTO> getById(@PathVariable("id") Long id) {
        Drug drug = drugService.getById(id);
        if (drug == null) {
            return Result.success(null);
        }
        return Result.success(new DrugSnapshotDTO(drug.getName(), drug.getImage(), drug.getPrice()));
    }

    @GetMapping("/{id}/info")
    public Result<DrugInfoDTO> getInfoById(@PathVariable("id") Long id) {
        Drug drug = drugService.getById(id);
        if (drug == null) {
            return Result.success(null);
        }
        return Result.success(new DrugInfoDTO(
                drug.getId(),
                drug.getName(),
                drug.getPrescriptionType(),
                drug.getManufacturer(),
                drug.getApprovalNumber(),
                drug.getIndications(),
                drug.getContraindications()));
    }

    @GetMapping("/search")
    public Result<List<DrugInfoDTO>> searchDrugByName(@RequestParam("name") String name) {
        List<DrugInfoDTO> list = drugService.searchDrugByName(name);
        return Result.success(list);
    }
}
