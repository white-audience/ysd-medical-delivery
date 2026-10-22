package com.ysd.controller.admin;

import com.ysd.dto.DrugComboDTO;
import com.ysd.dto.DrugComboPageQueryDTO;
import com.ysd.result.PageResult;
import com.ysd.result.Result;
import com.ysd.service.DrugComboService;
import com.ysd.vo.DrugComboVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 药品组合管理
 */
@RestController
@RequestMapping("/admin/drug-combo")
@Tag(name = "药品组合相关接口")
@Slf4j
public class DrugComboController {

    @Autowired
    private DrugComboService drugComboService;

    /**
     * 新增药品组合
     * @param drugComboDTO
     * @return
     */
    @PostMapping
    @Operation(summary = "新增药品组合")
    public Result save(@RequestBody DrugComboDTO drugComboDTO) {
        drugComboService.saveWithItems(drugComboDTO);
        return Result.success();
    }
    /**
     * 分页查询
     * @param drugComboPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询")
    public Result<PageResult> page(DrugComboPageQueryDTO drugComboPageQueryDTO) {
        PageResult pageResult = drugComboService.pageQuery(drugComboPageQueryDTO);
        return Result.success(pageResult);
    }
    /**
     * 批量删除药品组合
     * @param ids
     * @return
     */
    @DeleteMapping
    @Operation(summary = "批量删除药品组合")
    public Result delete(@RequestParam List<Long> ids){
        drugComboService.deleteBatch(ids);
        return Result.success();
    }


    /**
     * 根据id查询药品组合，用于修改页面回显数据
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据id查询药品组合")
    public Result<DrugComboVO> getById(@PathVariable Long id) {
        DrugComboVO drugComboVO = drugComboService.getByIdWithItems(id);
        return Result.success(drugComboVO);
    }

    /**
     * 修改药品组合
     *
     * @param drugComboDTO
     * @return
     */
    @PutMapping
    @Operation(summary = "修改药品组合")
    public Result update(@RequestBody DrugComboDTO drugComboDTO) {
        drugComboService.update(drugComboDTO);
        return Result.success();
    }

    /**
     * 药品组合在售停售
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @Operation(summary = "药品组合在售停售")
    public Result startOrStop(@PathVariable Integer status, Long id) {
        drugComboService.startOrStop(status, id);
        return Result.success();
    }
}