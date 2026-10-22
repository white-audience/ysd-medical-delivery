package com.ysd.mapper;

import com.github.pagehelper.Page;
import com.ysd.annotation.AutoFill;
import com.ysd.dto.DrugComboPageQueryDTO;
import com.ysd.entity.DrugCombo;
import com.ysd.enumeration.OperationType;
import com.ysd.vo.DrugComboVO;
import com.ysd.vo.DrugItemVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DrugComboMapper {

    /**
     * 根据分类id查询药品组合数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from drug_combo where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     * 新增药品组合
     * @param drugCombo
     */
    @AutoFill(OperationType.INSERT)
    void insert(DrugCombo drugCombo);

    /**
     * 分页查询
     * @param drugComboPageQueryDTO
     * @return
     */
    Page<DrugComboVO> pageQuery(DrugComboPageQueryDTO drugComboPageQueryDTO);

    /**
     * 根据id查询药品组合
     * @param id
     * @return
     */
    @Select("select * from drug_combo where id = #{id}")
    DrugCombo getById(Long id);

    /**
     * 根据id删除药品组合
     * @param id
     */
    @Delete("delete from drug_combo where id = #{id}")
    void deleteById(Long id);

    /**
     * 修改药品组合
     * @param drugCombo
     */
    void update(DrugCombo drugCombo);

    /**
     * 动态条件查询药品组合
     * @param drugCombo
     * @return
     */
    List<DrugCombo> list(DrugCombo drugCombo);

    /**
     * 根据药品组合id查询明细
     * @param comboId
     * @return
     */
    @Select("select sd.name, sd.quantity, d.image, d.description " +
            "from drug_combo_item sd left join drug d on sd.drug_id = d.id " +
            "where sd.combo_id = #{comboId}")
    List<DrugItemVO> getItemsByComboId(Long comboId);

    /**
     * 根据条件统计药品组合数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);
}
