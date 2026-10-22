package com.ysd.mapper;

import com.github.pagehelper.Page;
import com.ysd.annotation.AutoFill;
import com.ysd.dto.DrugPageQueryDTO;
import com.ysd.entity.Drug;
import com.ysd.enumeration.OperationType;
import com.ysd.vo.DrugVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DrugMapper {

    /**
     * 根据分类id查询药品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from drug where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     * 插入药品数据
     * @param drug
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(Drug drug);

    /**
     * 药品分页查询
     * @param drugPageQueryDTO
     * @return
     */
    Page<DrugVO> pageQuery(DrugPageQueryDTO drugPageQueryDTO);

    /**
     * 根据id查询药品
     * @param id
     * @return
     */
    @Select("select * from drug where id = #{id}")
    Drug getById(Long id);

    /**
     * 根据id删除药品数据
     * @param id
     */
    @Delete("delete from drug where id = #{id}")
    void deleteById(Long id);

    /**
     * 批量删除药品
     * @param ids
     */
    void deleteByIds(List<Long> ids);

    /**
     * 修改药品数据
     * @param drug
     */
    @AutoFill(value = OperationType.UPDATE)
    void update(Drug drug);

    /**
     * 动态条件查询药品
     * @param drug
     * @return
     */
    List<Drug> list(Drug drug);

    /**
     * 根据药品组合id查询药品
     * @param comboId
     * @return
     */
    @Select("select a.* from drug a left join drug_combo_item b on a.id = b.drug_id where b.combo_id = #{comboId}")
    List<Drug> getByComboId(Long comboId);

    /**
     * 根据条件统计药品数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);
}
