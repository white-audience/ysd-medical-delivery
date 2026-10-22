package com.ysd.service.impl;


import com.fasterxml.jackson.databind.util.BeanUtil;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ysd.api.dto.DrugInfoDTO;
import com.ysd.cache.CatalogCacheHelper;
import com.ysd.constant.MessageConstant;
import com.ysd.constant.StatusConstant;
import com.ysd.dto.DrugDTO;
import com.ysd.dto.DrugPageQueryDTO;
import com.ysd.entity.Drug;
import com.ysd.entity.DrugSpec;
import com.ysd.entity.DrugCombo;
import com.ysd.exception.DeletionNotAllowedException;
import com.ysd.exception.DrugComboEnableFailedException;
import com.ysd.mapper.DrugSpecMapper;
import com.ysd.mapper.DrugMapper;
import com.ysd.mapper.DrugComboItemMapper;
import com.ysd.mapper.DrugComboMapper;
import com.ysd.result.PageResult;
import com.ysd.service.DrugService;
import com.ysd.vo.DrugVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DrugServiceImpl implements DrugService {


    @Autowired
    private DrugMapper drugMapper;

    @Autowired
    private DrugSpecMapper specMapper;

    @Autowired
    private DrugComboItemMapper drugComboItemMapper;

    @Autowired
    private DrugComboMapper drugComboMapper;

    @Autowired
    private CatalogCacheHelper cacheHelper;
    /**
     * 新增药品和对应的规格
     * @param drugDTO
     */
    @Transactional
    public void saveWithSpecs(DrugDTO drugDTO) {
        Drug drug = new Drug();

        BeanUtils.copyProperties(drugDTO, drug);

        //向药品表插入1条数据
        drugMapper.insert(drug);

        Long drugId = drug.getId();

        List<DrugSpec> specs = drugDTO.getSpecs();
        if(specs != null && specs.size() > 0){
            specs.forEach(spec -> {
                spec.setDrugId(drugId);
            });
            //向规格表插入n条数据
            specMapper.insertBatch(specs);
        }

        cacheHelper.evict(CatalogCacheHelper.drugKey(drugDTO.getCategoryId()));
    }
    /**
     * 药品分页查询
     * @param drugPageQueryDTO
     * @return
     */
    public PageResult pageQuery(DrugPageQueryDTO drugPageQueryDTO){
        PageHelper.startPage(drugPageQueryDTO.getPage(),drugPageQueryDTO.getPageSize());
        Page<DrugVO> page=drugMapper.pageQuery(drugPageQueryDTO);

        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public Drug getById(Long id) {
        return drugMapper.getById(id);
    }

    /**
     * 批量删除药品
     * @param ids
     */
    @Transactional
    public void deleteBatch(List<Long> ids){
        //判断药品状态
        for (Long id : ids) {
            Drug drug = drugMapper.getById(id);
            if(drug.getStatus() == StatusConstant.ENABLE){
                //在售的药品不能删除
                throw new DeletionNotAllowedException(MessageConstant.DRUG_ON_SALE);
            }
        }

        //判断药品状态，关联药品组合的药品不能删除
        List<Long> comboIds = drugComboItemMapper.getComboIdsByDrugIds(ids);
        if(comboIds != null && comboIds.size() > 0){
            //存在关联, 抛出业务异常
            throw new DeletionNotAllowedException(MessageConstant.DRUG_BE_RELATED_BY_DRUG_COMBO);
        }
        //删除药品表中的药品数据
//        for (Long id : ids) {
//            drugMapper.deleteById(id);
//            //删除药品关联的规格数据
//            specMapper.deleteByDrugId(id);
//        }

        drugMapper.deleteByIds(ids);

        specMapper.deleteByDrugIds(ids);

        cacheHelper.evictByPattern(CatalogCacheHelper.DRUG_PATTERN);
    }

    @Override
    public DrugVO getByIdWithSpecs(Long id) {
        //1.根据id查询药品数据
        Drug drug = drugMapper.getById(id);
        //2.根据药品id查询规格数据
        List<DrugSpec> specs =specMapper.getByDrugId(id);
        //3.将2个list封装到DrugVO中并返回
        DrugVO drugVO = new DrugVO();
        BeanUtils.copyProperties(drug,drugVO);
        drugVO.setSpecs(specs);
        return drugVO;
    }

    /**
     * 修改药品和规格数据
     * @param drugDTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWithSpecs(DrugDTO drugDTO) {
        // 第一次删除（写库前）：即使后续事务回滚，此处也只是一次"白删"，
        // 不会留下脏数据——下次读缓存 miss 会回源到库中的旧值。
        cacheHelper.evictByPattern(CatalogCacheHelper.DRUG_PATTERN);

        Drug drug = new Drug();
        BeanUtils.copyProperties(drugDTO,drug);
        //1.修改药品表
       drugMapper.update(drug);
        //删除药品表中的规格数据
        specMapper.deleteByDrugId(drugDTO.getId());
        //插入新的规格数据
        List<DrugSpec> specs = drugDTO.getSpecs();
        if(specs != null && specs.size() > 0){
            specs.forEach(spec -> {
                spec.setDrugId(drugDTO.getId());
            });
            //向规格表插入n条数据
            specMapper.insertBatch(specs);
        }

        // 第二次删除（延迟双删）：事务提交后延时 500ms 再清一次，清掉窗口期内晚到的旧值回写
        cacheHelper.scheduleDelayedEvict(CatalogCacheHelper.DRUG_PATTERN, 500);
    }
    /**
     * 根据分类id查询药品
     * @param categoryId
     * @return
     */
    public List<Drug> list(Long categoryId) {
        Drug drug = Drug.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();
        return drugMapper.list(drug);
    }

    /**
     * 药品在售、停售
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, Long id) {
            //select a.* from drug a left join drug_combo_item b on a.id = b.drug_id where b.combo_id = ?
        Drug  drug = Drug.builder()
                .id(id)
                .status(status)
                .build();
        drugMapper.update(drug);

        cacheHelper.evictByPattern(CatalogCacheHelper.DRUG_PATTERN);
    }

    /**
     * 条件查询药品和规格
     * @param drug
     * @return
     */
    public List<DrugVO> listWithSpecs(Drug drug) {
        String key = CatalogCacheHelper.drugKey(drug.getCategoryId());
        return cacheHelper.getWithLock(key, CatalogCacheHelper.CacheType.DRUG, () -> {
            List<Drug> drugList = drugMapper.list(drug);

            List<DrugVO> drugVOList = new ArrayList<>();

            for (Drug d : drugList) {
                DrugVO drugVO = new DrugVO();
                BeanUtils.copyProperties(d, drugVO);

                //根据药品id查询对应的规格
                List<DrugSpec> specs = specMapper.getByDrugId(d.getId());

                drugVO.setSpecs(specs);
                drugVOList.add(drugVO);
            }

            return drugVOList;
        });
    }

    @Override
    public List<DrugInfoDTO> searchDrugByName(String name) {
        List<Drug> drugs = drugMapper.searchDrugByName(name);
        return drugs.stream()
                .map(d -> new DrugInfoDTO(
                        d.getId(),
                        d.getName(),
                        d.getPrescriptionType(),
                        d.getManufacturer(),
                        d.getApprovalNumber(),
                        d.getIndications(),
                        d.getContraindications()))
                .collect(Collectors.toList());
    }
}
