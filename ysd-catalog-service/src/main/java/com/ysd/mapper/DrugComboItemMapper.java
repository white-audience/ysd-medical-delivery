package com.ysd.mapper;

import com.ysd.entity.DrugComboItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DrugComboItemMapper {
    /**
     * 根据药品id查询药品组合id
     * @param drugIds
     * @return
     */
    List<Long> getComboIdsByDrugIds(@Param("drugIds") List<Long> drugIds);

    /**
     * 批量保存药品组合明细
     * @param items
     */
    void insertBatch(@Param("items") List<DrugComboItem> items);

    /**
     * 根据药品组合id删除明细
     * @param comboId
     */
    @Delete("delete from drug_combo_item where combo_id = #{comboId}")
    void deleteByComboId(Long comboId);

    /**
     * 根据药品组合id查询明细
     * @param comboId
     * @return
     */
    @Select("select * from drug_combo_item where combo_id = #{comboId}")
    List<DrugComboItem> getByComboId(Long comboId);
}
