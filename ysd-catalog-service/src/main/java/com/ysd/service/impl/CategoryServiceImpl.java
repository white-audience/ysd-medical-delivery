package com.ysd.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ysd.cache.CatalogCacheHelper;
import com.ysd.constant.MessageConstant;
import com.ysd.constant.StatusConstant;
import com.ysd.context.BaseContext;
import com.ysd.dto.CategoryDTO;
import com.ysd.dto.CategoryPageQueryDTO;
import com.ysd.entity.Category;
import com.ysd.exception.DeletionNotAllowedException;
import com.ysd.mapper.CategoryMapper;
import com.ysd.mapper.DrugMapper;
import com.ysd.mapper.DrugComboMapper;
import com.ysd.result.PageResult;
import com.ysd.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分类业务层
 */
@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private DrugMapper drugMapper;
    @Autowired
    private DrugComboMapper drugComboMapper;

    @Autowired
    private CatalogCacheHelper cacheHelper;

    /**
     * 新增分类
     * @param categoryDTO
     */
    public void save(CategoryDTO categoryDTO) {
        Category category = new Category();
        //属性拷贝
        BeanUtils.copyProperties(categoryDTO, category);

        //分类状态默认为禁用状态0
        category.setStatus(StatusConstant.DISABLE);

        //设置创建时间、修改时间、创建人、修改人
//        category.setCreateTime(LocalDateTime.now());
//        category.setUpdateTime(LocalDateTime.now());
//        category.setCreateUser(BaseContext.getCurrentId());
//        category.setUpdateUser(BaseContext.getCurrentId());

        categoryMapper.insert(category);

        cacheHelper.evictByPattern(CatalogCacheHelper.CATEGORY_PATTERN);
    }

    /**
     * 分页查询
     * @param categoryPageQueryDTO
     * @return
     */
    public PageResult pageQuery(CategoryPageQueryDTO categoryPageQueryDTO) {
        PageHelper.startPage(categoryPageQueryDTO.getPage(),categoryPageQueryDTO.getPageSize());
        //下一条sql进行分页，自动加入limit关键字分页
        Page<Category> page = categoryMapper.pageQuery(categoryPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 根据id删除分类
     * @param id
     */
    public void deleteById(Long id) {
        //查询当前分类是否关联了药品，如果关联了就抛出业务异常
        Integer count = drugMapper.countByCategoryId(id);
        if(count > 0){
            //当前分类下有药品，不能删除
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_DRUG);
        }

        //查询当前分类是否关联了药品组合，如果关联了就抛出业务异常
        count = drugComboMapper.countByCategoryId(id);
        if(count > 0){
            //当前分类下有药品，不能删除
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_DRUG_COMBO);
        }

        //删除分类数据
        categoryMapper.deleteById(id);

        cacheHelper.evictByPattern(CatalogCacheHelper.CATEGORY_PATTERN);
    }

    /**
     * 修改分类
     * @param categoryDTO
     */
    public void update(CategoryDTO categoryDTO) {
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO,category);

        //设置修改时间、修改人
//        category.setUpdateTime(LocalDateTime.now());
//        category.setUpdateUser(BaseContext.getCurrentId());

        categoryMapper.update(category);

        cacheHelper.evictByPattern(CatalogCacheHelper.CATEGORY_PATTERN);
    }

    /**
     * 启用、禁用分类
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, Long id) {
        Category category = Category.builder()
                .id(id)
                .status(status)
//                .updateTime(LocalDateTime.now())
//                .updateUser(BaseContext.getCurrentId())
                .build();
        categoryMapper.update(category);

        cacheHelper.evictByPattern(CatalogCacheHelper.CATEGORY_PATTERN);
    }

    /**
     * 根据类型查询分类
     * @param type
     * @return
     */
    public List<Category> list(Integer type) {
        String key = CatalogCacheHelper.categoryKey(type);
        return cacheHelper.getWithLock(key, CatalogCacheHelper.CacheType.CATEGORY,
                () -> categoryMapper.list(type));
    }
}
