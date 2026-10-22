package com.ysd.exception;

/**
 * 药品组合启用失败异常
 */
public class DrugComboEnableFailedException extends BaseException {

    public DrugComboEnableFailedException(){}

    public DrugComboEnableFailedException(String msg){
        super(msg);
    }
}
