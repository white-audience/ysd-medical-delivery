package com.ysd.service;

import com.ysd.dto.DrugComboDTO;
import com.ysd.dto.DrugComboPageQueryDTO;
import com.ysd.entity.DrugCombo;
import com.ysd.result.PageResult;
import com.ysd.vo.DrugItemVO;
import com.ysd.vo.DrugComboVO;

import java.util.List;

public interface DrugComboService {

    /**
     * 新增药品组合，同时需要保存药品组合明细
     * @param drugComboDTO
     */
    void saveWithItems(DrugComboDTO drugComboDTO);
    /**
     * 分页查询
     * @param drugComboPageQueryDTO
     * @return
     */
    PageResult pageQuery(DrugComboPageQueryDTO drugComboPageQueryDTO);

    /**
     * 批量删除药品组合
     * @param ids
     */
    void deleteBatch(List<Long> ids);

    /**
     * 根据id查询药品组合和关联的药品数据
     * @param id
     * @return
     */
    DrugComboVO getByIdWithItems(Long id);

    /**
     * 修改药品组合
     * @param drugComboDTO
     */
    void update(DrugComboDTO drugComboDTO);

    /**
     * 药品组合在售、停售
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);
    /**
     * 条件查询
     * @param drugCombo
     * @return
     */
    List<DrugCombo> list(DrugCombo drugCombo);

    /**
     * 根据id查询药品明细
     * @param id
     * @return
     */
    List<DrugItemVO> getItemsByComboId(Long id);

    /**
     * 根据id查询药品组合（内网 /inner 接口用，仅返回实体）
     * @param id
     * @return
     */
    DrugCombo getById(Long id);
}