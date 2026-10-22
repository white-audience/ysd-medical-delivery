package com.ysd.service;

import com.ysd.dto.ShoppingCartDTO;
import com.ysd.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {

    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);
/**
     * 查看购物车
     * @return
     */
    List<ShoppingCart> showShoppingCart();
/**
     * 清空购物车
     */
    void cleanShoppingCart();
}
