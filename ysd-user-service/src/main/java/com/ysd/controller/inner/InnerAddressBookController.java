package com.ysd.controller.inner;

import com.ysd.api.dto.AddressBookSnapshotDTO;
import com.ysd.constant.MessageConstant;
import com.ysd.context.BaseContext;
import com.ysd.entity.AddressBook;
import com.ysd.result.Result;
import com.ysd.service.AddressBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内网接口：供 order-service 通过 Feign 调用（下单时取收货人 / 电话）。
 * 无登录校验，仅由网关屏蔽外部访问；身份经 X-Identity-Id 透传（见 InnerIdentityInterceptor）。
 */
@RestController
@RequestMapping("/inner/addressBook")
public class InnerAddressBookController {

    @Autowired
    private AddressBookService addressBookService;

    @GetMapping("/{id}")
    public Result<AddressBookSnapshotDTO> getById(@PathVariable("id") Long id) {
        // 身份来自 Feign 透传的 X-Identity-Id（网关已剥离客户端伪造头），不接受调用方传 userId
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            return Result.error(MessageConstant.USER_NOT_LOGIN);
        }
        AddressBook addressBook = addressBookService.getByIdAndUserId(id, userId);
        if (addressBook == null) {
            return Result.error(MessageConstant.ADDRESS_BOOK_NOT_EXIST_OR_DENIED);
        }
        return Result.success(new AddressBookSnapshotDTO(addressBook.getConsignee(), addressBook.getPhone()));
    }
}
