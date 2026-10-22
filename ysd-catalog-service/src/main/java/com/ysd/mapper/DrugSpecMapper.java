package com.ysd.mapper;

import com.ysd.annotation.AutoFill;
import com.ysd.entity.DrugSpec;
import com.ysd.enumeration.OperationType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DrugSpecMapper {


    /**
     * 批量插入药品规格数据
     * @param specs
     */
    void insertBatch(List<DrugSpec> specs);


    /**
     * 删除指定药品的规格数据
     * @param drugId
     */
    @Delete("delete from drug_spec where drug_id = #{drugId}")
    void deleteByDrugId(Long drugId);


    /**
     * 批量删除指定药品的规格数据
     * @param drugIds
     */
    void deleteByDrugIds(@Param("drugIds") List<Long> drugIds);

    /**
     * 根据药品id查询对应的规格数据
     * @param drugId
     * @return
     */
@Select( "select * from drug_spec where drug_id = #{drugId}")
    List<DrugSpec> getByDrugId(Long drugId);
}
