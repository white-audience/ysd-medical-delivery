package com.ysd.controller.inner;

import com.ysd.api.dto.DrugComboSnapshotDTO;
import com.ysd.entity.DrugCombo;
import com.ysd.result.Result;
import com.ysd.service.DrugComboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内网接口：供 order-service 通过 Feign 调用（加购物车时取 name / image / price）。
 * 无登录校验，仅由网关屏蔽外部访问。
 */
@RestController
@RequestMapping("/inner/drug-combo")
public class InnerDrugComboController {

    @Autowired
    private DrugComboService drugComboService;

    @GetMapping("/{id}")
    public Result<DrugComboSnapshotDTO> getById(@PathVariable("id") Long id) {
        DrugCombo drugCombo = drugComboService.getById(id);
        if (drugCombo == null) {
            return Result.success(null);
        }
        return Result.success(new DrugComboSnapshotDTO(drugCombo.getName(), drugCombo.getImage(), drugCombo.getPrice()));
    }
}
