package com.ysd.service;

import com.ysd.api.dto.DrugInfoDTO;
import com.ysd.dto.DrugDTO;
import com.ysd.dto.DrugPageQueryDTO;
import com.ysd.entity.Drug;
import com.ysd.result.PageResult;
import com.ysd.vo.DrugVO;

import java.util.List;

public interface DrugService {
    /**
     * 新增药品和对应的规格
     * @param drugDTO
     */
    public void saveWithSpecs(DrugDTO drugDTO);
/**
     * 药品分页查询
     * @param drugPageQueryDTO
     * @return
     */
    PageResult pageQuery(DrugPageQueryDTO drugPageQueryDTO);


    /**
     * 批量删除药品
     * @param ids
     */
    void deleteBatch(List<Long> ids);
/**
     * 根据id查询药品和对应的规格数据
     * @param id
     * @return
     */
    DrugVO getByIdWithSpecs(Long id);
/**
     * 修改药品和规格数据
     * @param drugDTO
     */
    void updateWithSpecs(DrugDTO drugDTO);
    /**
     * 根据分类id查询药品
     * @param categoryId
     * @return
     */
    List<Drug> list(Long categoryId);

    /**
     * 条件查询药品和规格
     * @param drug
     * @return
     */
    List<DrugVO> listWithSpecs(Drug drug);
/**
     * 批量在售停售
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 根据id查询药品（内网 /inner 接口用，仅返回实体）
     * @param id
     * @return
     */
    Drug getById(Long id);

    List<DrugInfoDTO> searchDrugByName(String name);
}
