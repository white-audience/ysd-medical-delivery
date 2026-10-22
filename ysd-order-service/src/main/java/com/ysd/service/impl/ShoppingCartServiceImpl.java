package com.ysd.service.impl;

import com.ysd.api.client.CatalogClient;
import com.ysd.api.dto.DrugSnapshotDTO;
import com.ysd.api.dto.DrugComboSnapshotDTO;
import com.ysd.context.BaseContext;
import com.ysd.dto.ShoppingCartDTO;
import com.ysd.entity.ShoppingCart;
import com.ysd.exception.ShoppingCartBusinessException;
import com.ysd.mapper.ShoppingCartMapper;
import com.ysd.result.Result;
import com.ysd.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {


    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private CatalogClient catalogClient;

    /*
    添加购物车
    @param shoppingCartDTO
     */
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        //判断当前添加的购物车项是否在购物车中
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        //如果存在，则数量加1
        if(list != null && list.size() > 0){
            ShoppingCart cart = list.get(0);
            cart.setNumber(cart.getNumber() + 1);
            shoppingCartMapper.updateNumberById( cart);
        }
        else{
            //如果不存在，则添加到购物车，数量默认为1

            //判断本次添加到购物车为药品还是药品组合
            Long drugId = shoppingCartDTO.getDrugId();
            if(drugId != null){
                DrugSnapshotDTO drug = unwrap(catalogClient.getDrugById(drugId), "查询药品");
                shoppingCart.setName(drug.getName());
                shoppingCart.setImage(drug.getImage());
                shoppingCart.setAmount(drug.getPrice());
            }else {
                Long comboId = shoppingCartDTO.getComboId();

                DrugComboSnapshotDTO drugCombo = unwrap(catalogClient.getDrugComboById(comboId), "查询药品组合");
                shoppingCart.setName(drugCombo.getName());
                shoppingCart.setImage(drugCombo.getImage());
                shoppingCart.setAmount(drugCombo.getPrice());
            }
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
           shoppingCartMapper.insert(shoppingCart);
        }
    }

    @Override
    public List<ShoppingCart> showShoppingCart() {
        // 获取当前用户
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .build();
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        return list;
    }
/**
 * 清空购物车
 */
    @Override
    public void cleanShoppingCart() {
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.deleteByUserId(userId);
    }

    /**
     * 拆 Feign 返回的 Result：调用失败或数据缺失一律抛业务异常（阶段 5 fail-fast，暂无降级）。
     */
    private <T> T unwrap(Result<T> result, String action) {
        if (result == null || result.getCode() == null || result.getCode() != 1) {
            throw new ShoppingCartBusinessException(action + "失败：" + (result == null ? "无响应" : result.getMsg()));
        }
        if (result.getData() == null) {
            throw new ShoppingCartBusinessException(action + "失败：数据不存在");
        }
        return result.getData();
    }
}
