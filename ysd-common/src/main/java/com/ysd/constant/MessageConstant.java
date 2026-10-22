package com.ysd.constant;

/**
 * 信息提示常量类
 */
public class MessageConstant {

    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String ALREADY_EXISTS = "账号已存在";
    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String USER_NOT_LOGIN = "用户未登录";
    public static final String CATEGORY_BE_RELATED_BY_DRUG_COMBO = "当前分类关联了药品组合,不能删除";
    public static final String CATEGORY_BE_RELATED_BY_DRUG = "当前分类关联了药品,不能删除";
    public static final String SHOPPING_CART_IS_NULL = "购物车数据为空，不能下单";
    public static final String ADDRESS_BOOK_IS_NULL = "用户地址为空，不能下单";
    public static final String ADDRESS_BOOK_NOT_EXIST_OR_DENIED = "地址不存在或无权限";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String UPLOAD_FAILED = "文件上传失败";
    public static final String DRUG_COMBO_ENABLE_FAILED = "药品组合内包含未在售药品，无法上架";
    public static final String PASSWORD_EDIT_FAILED = "密码修改失败";
    public static final String DRUG_ON_SALE = "在售的药品不能删除";
    public static final String DRUG_COMBO_ON_SALE = "在售的药品组合不能删除";
    public static final String DRUG_BE_RELATED_BY_DRUG_COMBO = "当前药品关联了药品组合,不能删除";
    public static final String ORDER_STATUS_ERROR = "订单状态错误";
    public static final String ORDER_NOT_FOUND = "订单不存在";
    public static final String PRESCRIPTION_REQUIRED = "购物车包含处方药，请先上传处方后再下单";
    public static final String PRESCRIPTION_AUDIT_REJECTED = "处方审核未通过，禁止下单";
    public static final String PRESCRIPTION_AUDIT_UNAVAILABLE =
            "处方审核服务暂时不可用，为保障用药安全暂不能下单，请稍后重试";
    public static final String DRUG_INFO_UNAVAILABLE =
            "药品信息服务暂时不可用，为保障用药安全暂不能下单，请稍后重试";

}
