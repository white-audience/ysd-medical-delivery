package com.ysd.controller.admin;

import com.ysd.dto.DrugDTO;
import com.ysd.dto.DrugPageQueryDTO;
import com.ysd.entity.Drug;
import com.ysd.result.PageResult;
import com.ysd.result.Result;
import com.ysd.service.DrugService;
import com.ysd.vo.DrugVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Description:
 **/
@RestController
@RequestMapping("/admin/drug")
@Tag(name = "药品相关接口")
@Slf4j
public class DrugController {


    @Autowired
    private DrugService drugService;
/**
 * 新增药品
 * @param drugDTO
 * @return
 */
    @RequestMapping
    @Operation(summary = "新增药品")
public Result save(@RequestBody DrugDTO drugDTO){
        log.info("新增药品：{}", drugDTO);
        drugService.saveWithSpecs(drugDTO);
        return Result.success();
}

/**
 * 药品分页查询
 * @param drugPageQueryDTO
 * @return
 */
@GetMapping("/page")
@Operation(summary = "药品分页查询")
public Result<PageResult> page(DrugPageQueryDTO drugPageQueryDTO){
    log.info("药品分页查询：{}", drugPageQueryDTO);
    PageResult pageResult = drugService.pageQuery(drugPageQueryDTO);
    return Result.success(pageResult);
}
 /**
 * 批量删除药品
 * @param ids
 * @return
 */
    @DeleteMapping
    @Operation(summary = "批量删除药品")
    public Result delete(@RequestParam List<Long> ids){
        log.info("删除药品：{}", ids);
        drugService.deleteBatch(ids);
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @Operation(summary = "在售、停售药品")
    public Result startOrStop(@PathVariable Integer status, Long id){
        log.info("在售、停售药品：{}", id);
        drugService.startOrStop(status, id);
        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据id查询药品")
    public Result<DrugVO> getById(@PathVariable Long id){
        log.info("获取药品信息：{}", id);
        DrugVO drugVO = drugService.getByIdWithSpecs(id);
        return Result.success(drugVO);
    }
    /**
     * 修改药品
     * @param drugDTO
     * @return
     */
    @PutMapping
    @Operation(summary = "修改药品")
    public Result update(@RequestBody DrugDTO drugDTO){
        log.info("修改药品：{}", drugDTO);
        drugService.updateWithSpecs(drugDTO);
        return Result.success();
    }
    /**
     * 根据分类id查询药品
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @Operation(summary = "根据分类id查询药品")
    public Result<List<Drug>> list(Long categoryId){
        List<Drug> list = drugService.list(categoryId);

        return Result.success(list);
    }
}
