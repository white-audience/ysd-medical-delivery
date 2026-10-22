package com.ysd.service;

import com.ysd.dto.UserLoginDTO;
import com.ysd.entity.User;

public interface UserService {


    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    User wxLogin(UserLoginDTO userLoginDTO);
}
