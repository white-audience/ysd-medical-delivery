package com.ysd.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ysd.cache.CatalogCacheHelper;
import com.ysd.constant.MessageConstant;
import com.ysd.constant.StatusConstant;
import com.ysd.dto.DrugComboDTO;
import com.ysd.dto.DrugComboPageQueryDTO;
import com.ysd.entity.Drug;
import com.ysd.entity.DrugCombo;
import com.ysd.entity.DrugComboItem;
import com.ysd.exception.DeletionNotAllowedException;
import com.ysd.exception.DrugComboEnableFailedException;
import com.ysd.mapper.DrugMapper;
import com.ysd.mapper.DrugComboItemMapper;
import com.ysd.mapper.DrugComboMapper;
import com.ysd.result.PageResult;
import com.ysd.service.DrugComboService;
import com.ysd.vo.DrugItemVO;
import com.ysd.vo.DrugComboVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 药品组合业务实现
 */
@Service
@Slf4j
public class DrugComboServiceImpl implements DrugComboService {

    @Autowired
    private DrugComboMapper drugComboMapper;
    @Autowired
    private DrugComboItemMapper drugComboItemMapper;
    @Autowired
    private DrugMapper drugMapper;

    @Autowired
    private CatalogCacheHelper cacheHelper;

    /**
     * 新增药品组合，同时需要保存药品组合明细
     * @param drugComboDTO
     */
    @Transactional
    public void saveWithItems(DrugComboDTO drugComboDTO) {
        DrugCombo drugCombo = new DrugCombo();
        BeanUtils.copyProperties(drugComboDTO, drugCombo);

        //向药品组合表插入数据
        drugComboMapper.insert(drugCombo);

        //获取生成的药品组合id
        Long comboId = drugCombo.getId();

        List<DrugComboItem> items = drugComboDTO.getItems();
        items.forEach(item -> {
            item.setComboId(comboId);
        });

        //保存药品组合明细
        drugComboItemMapper.insertBatch(items);

        cacheHelper.evict(CatalogCacheHelper.comboKey(drugComboDTO.getCategoryId()));
    }
    /**
     * 分页查询
     * @param drugComboPageQueryDTO
     * @return
     */
    public PageResult pageQuery(DrugComboPageQueryDTO drugComboPageQueryDTO) {
        int pageNum = drugComboPageQueryDTO.getPage();
        int pageSize = drugComboPageQueryDTO.getPageSize();

        PageHelper.startPage(pageNum, pageSize);
        Page<DrugComboVO> page = drugComboMapper.pageQuery(drugComboPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除药品组合
     * @param ids
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        ids.forEach(id -> {
            DrugCombo drugCombo = drugComboMapper.getById(id);
            if(StatusConstant.ENABLE == drugCombo.getStatus()){
                //在售的药品组合不能删除
                throw new DeletionNotAllowedException(MessageConstant.DRUG_COMBO_ON_SALE);
            }
        });

        ids.forEach(comboId -> {
            //删除药品组合表中的数据
            drugComboMapper.deleteById(comboId);
            //删除药品组合明细表中的数据
            drugComboItemMapper.deleteByComboId(comboId);
        });

        cacheHelper.evictByPattern(CatalogCacheHelper.COMBO_PATTERN);
    }


    /**
     * 根据id查询药品组合和药品组合明细
     *
     * @param id
     * @return
     */
    public DrugComboVO getByIdWithItems(Long id) {
        DrugCombo drugCombo = drugComboMapper.getById(id);
        List<DrugComboItem> items = drugComboItemMapper.getByComboId(id);

        DrugComboVO drugComboVO = new DrugComboVO();
        BeanUtils.copyProperties(drugCombo, drugComboVO);
        drugComboVO.setItems(items);

        return drugComboVO;
    }

    /**
     * 修改药品组合
     *
     * @param drugComboDTO
     */
    @Transactional
    public void update(DrugComboDTO drugComboDTO) {
        DrugCombo drugCombo = new DrugCombo();
        BeanUtils.copyProperties(drugComboDTO, drugCombo);

        //1、修改药品组合表，执行update
        drugComboMapper.update(drugCombo);

        //药品组合id
        Long comboId = drugComboDTO.getId();

        //2、删除药品组合明细，操作drug_combo_item表，执行delete
        drugComboItemMapper.deleteByComboId(comboId);

        List<DrugComboItem> items = drugComboDTO.getItems();
        items.forEach(item -> {
            item.setComboId(comboId);
        });
        //3、重新插入药品组合明细，操作drug_combo_item表，执行insert
        drugComboItemMapper.insertBatch(items);

        cacheHelper.evictByPattern(CatalogCacheHelper.COMBO_PATTERN);
    }

    /**
     * 药品组合在售、停售
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, Long id) {
        //上架药品组合时，判断药品组合内是否有停售药品，有停售药品提示"药品组合内包含未在售药品，无法上架"
        if(status == StatusConstant.ENABLE){
            //select a.* from drug a left join drug_combo_item b on a.id = b.drug_id where b.combo_id = ?
            List<Drug> drugList = drugMapper.getByComboId(id);
            if(drugList != null && drugList.size() > 0){
                drugList.forEach(drug -> {
                    if(StatusConstant.DISABLE == drug.getStatus()){
                        throw new DrugComboEnableFailedException(MessageConstant.DRUG_COMBO_ENABLE_FAILED);
                    }
                });
            }
        }

        DrugCombo drugCombo = DrugCombo.builder()
                .id(id)
                .status(status)
                .build();
        drugComboMapper.update(drugCombo);

        cacheHelper.evictByPattern(CatalogCacheHelper.COMBO_PATTERN);
    }
    /**
     * 条件查询
     * @param drugCombo
     * @return
     */
    public List<DrugCombo> list(DrugCombo drugCombo) {
        String key = CatalogCacheHelper.comboKey(drugCombo.getCategoryId());
        return cacheHelper.getWithLock(key, CatalogCacheHelper.CacheType.COMBO,
                () -> drugComboMapper.list(drugCombo));
    }

    /**
     * 根据id查询药品明细
     * @param id
     * @return
     */
    public List<DrugItemVO> getItemsByComboId(Long id) {
        return drugComboMapper.getItemsByComboId(id);
    }

    @Override
    public DrugCombo getById(Long id) {
        return drugComboMapper.getById(id);
    }
}