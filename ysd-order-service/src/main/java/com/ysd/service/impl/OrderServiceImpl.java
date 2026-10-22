package com.ysd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ysd.api.client.AiPrescriptionClient;
import com.ysd.api.client.CatalogClient;
import com.ysd.api.client.UserClient;
import com.ysd.api.dto.AddressBookSnapshotDTO;
import com.ysd.api.dto.DrugInfoDTO;
import com.ysd.api.dto.PrescriptionCheckRequest;
import com.ysd.api.dto.PrescriptionCheckResponse;
import com.ysd.constant.MessageConstant;
import com.ysd.context.BaseContext;
import com.ysd.dto.OrdersCancelDTO;
import com.ysd.dto.OrdersConfirmDTO;
import com.ysd.dto.OrdersPageQueryDTO;
import com.ysd.dto.OrdersPaymentDTO;
import com.ysd.dto.OrdersRejectionDTO;
import com.ysd.dto.OrdersSubmitDTO;
import com.ysd.entity.OrderDetail;
import com.ysd.entity.Orders;
import com.ysd.entity.ShoppingCart;
import com.ysd.exception.AddressBookBusinessException;
import com.ysd.exception.OrderBusinessException;
import com.ysd.exception.ShoppingCartBusinessException;
import com.ysd.mapper.OrderDetailMapper;
import com.ysd.mapper.OrderMapper;
import com.ysd.mapper.ShoppingCartMapper;
import com.ysd.result.PageResult;
import com.ysd.result.Result;
import com.ysd.service.OrderService;
import com.ysd.utils.WeChatPayUtil;
import com.ysd.vo.OrderPaymentVO;
import com.ysd.vo.OrderStatisticsVO;
import com.ysd.vo.OrderSubmitVO;
import com.ysd.vo.OrderVO;
import com.ysd.mq.WsPushProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserClient userClient;
    @Autowired
    private CatalogClient catalogClient;
    @Autowired
    private AiPrescriptionClient aiPrescriptionClient;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WsPushProducer wsPushProducer;
    /*
    用户下单
    @param ordersSubmitDTO
    @return
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {


        //1处理业务异常（地址簿为空，购物车数据为空）
        AddressBookSnapshotDTO addressBook = unwrap(userClient.getAddressBookById(ordersSubmitDTO.getAddressBookId()), "查询地址簿");
        if(addressBook == null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //查询当前用户购物车数据
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);

        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);


        if(shoppingCartList == null || shoppingCartList.size() == 0){
            //抛出业务异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 1.5 处方药强制审核：含处方药时必须通过 AI 审核，超时/异常一律 fail-closed
        checkPrescriptionAudit(shoppingCartList, ordersSubmitDTO, userId);

        // 2生成订单号,想订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        // DB 中 tableware_status 为 NOT NULL；客户端未传时按默认“按餐量提供”落库
        if (orders.getTablewareStatus() == null) {
            orders.setTablewareStatus(1);
        }
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);

        orderMapper.insert(orders);

        List <OrderDetail> orderDetailList = new ArrayList<>();
        //3想订单明细表插入n条数据
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }


        orderDetailMapper.insertBatch(orderDetailList);
        //4清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        //5封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();

        return orderSubmitVO;
    }

    /**
     * 处方药下单审核：
     * <ol>
     *   <li>按药品 ID 查询 prescriptionType，购物车不含处方药则直接放行；</li>
     *   <li>含处方药但未带处方凭证，直接拦截；</li>
     *   <li>含处方药且已带凭证，调用 ai-service 内部审核接口；</li>
     *   <li>AI 超时、异常、熔断或返回不通过，一律拦截（fail-closed）。</li>
     * </ol>
     */
    private void checkPrescriptionAudit(List<ShoppingCart> cartList, OrdersSubmitDTO dto, Long userId) {
        List<Long> drugIds = cartList.stream()
                .map(ShoppingCart::getDrugId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (drugIds.isEmpty()) {
            return;
        }

        List<DrugInfoDTO> drugs = new ArrayList<>();
        try {
            for (Long drugId : drugIds) {
                Result<DrugInfoDTO> result = catalogClient.getDrugInfoById(drugId);
                if (result == null || result.getCode() == null || result.getCode() != 1
                        || result.getData() == null) {
                    log.error("下单查询药品信息失败，drugId={}, result={}", drugId, result);
                    throw new OrderBusinessException(MessageConstant.DRUG_INFO_UNAVAILABLE);
                }
                drugs.add(result.getData());
            }
        } catch (OrderBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("下单查询药品信息异常，drugIds={}", drugIds, e);
            throw new OrderBusinessException(MessageConstant.DRUG_INFO_UNAVAILABLE);
        }

        boolean containsPrescriptionDrug = drugs.stream()
                .anyMatch(d -> Integer.valueOf(1).equals(d.getPrescriptionType()));
        if (!containsPrescriptionDrug) {
            return;
        }

        if (dto.getPrescriptionUrl() == null || dto.getPrescriptionUrl().isBlank()) {
            throw new OrderBusinessException(MessageConstant.PRESCRIPTION_REQUIRED);
        }

        Result<PrescriptionCheckResponse> auditResult;
        try {
            auditResult = aiPrescriptionClient.checkPrescription(
                    new PrescriptionCheckRequest(drugIds, dto.getPrescriptionUrl(), userId));
        } catch (Exception e) {
            log.error("AI 处方审核调用异常，按 fail-closed 拦截下单，userId={}", userId, e);
            throw new OrderBusinessException(MessageConstant.PRESCRIPTION_AUDIT_UNAVAILABLE);
        }

        if (auditResult == null || auditResult.getCode() == null || auditResult.getCode() != 1
                || auditResult.getData() == null) {
            log.error("AI 处方审核返回异常，userId={}, result={}", userId, auditResult);
            throw new OrderBusinessException(MessageConstant.PRESCRIPTION_AUDIT_UNAVAILABLE);
        }

        PrescriptionCheckResponse audit = auditResult.getData();
        if (!audit.approved()) {
            String reason = audit.reason() == null || audit.reason().isBlank()
                    ? MessageConstant.PRESCRIPTION_AUDIT_REJECTED : audit.reason();
            log.warn("AI 处方审核拒绝下单，userId={}, reason={}", userId, reason);
            throw new OrderBusinessException(reason);
        }
        log.info("AI 处方审核通过，userId={}, drugs={}", userId, audit.prescriptionDrugs());
    }

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception{
        //调用微信支付接口，生成预支付交易单
        /*JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "药速达订单", //药品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }*/

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", "ORDERPAID");
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        //为替代微信支付成功后的数据库订单状态更新，多定义一个方法进行修改
        Integer OrderPaidStatus = Orders.PAID; //支付状态，已支付
        Integer OrderStatus = Orders.TO_BE_CONFIRMED;  //订单状态，待接单

        //发现没有将支付时间 check_out属性赋值，所以在这里更新
        LocalDateTime check_out_time = LocalDateTime.now();

        //获取订单号码
        String orderNumber = ordersPaymentDTO.getOrderNumber();

        log.info("调用updateStatus，用于替换微信支付更新数据库状态的问题");
        orderMapper.updateStatus(OrderStatus, OrderPaidStatus, check_out_time, orderNumber);

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);


        // 通过WebSocket向客户端推送消息
        Map map= new HashMap();
        map.put("type", 1);//1表示来来单提醒 2表示来客户催单
        map.put("orderId", ordersDB.getId());
        map.put("content", "订单号："+outTradeNo);

        String json = JSON.toJSONString(map);
        wsPushProducer.push(json);
    }
    /**
     * 用户端订单分页查询
     *
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        // 设置分页
        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        // 分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = new ArrayList();

        // 查询出订单明细，并封装入OrderVO进行响应
        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                Long orderId = orders.getId();// 订单id

                // 查询订单明细
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails);

                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), list);
    }
    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    public OrderVO details(Long id) {
        // 根据id查询订单
        Orders orders = orderMapper.getById(id);

        // 查询该订单对应的药品/药品组合明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将该订单及其详情封装到OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }
    /**
     * 用户取消订单
     *
     * @param id
     */
    public void userCancelById(Long id) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (ordersDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        // 订单处于待接单状态下取消，需要进行退款
        if (ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            //调用微信支付退款接口
            weChatPayUtil.refund(
                    ordersDB.getNumber(), //商户订单号
                    ordersDB.getNumber(), //商户退款单号
                    ordersDB.getAmount(), //退款金额，单位 元
                    ordersDB.getAmount());//原订单金额

            //支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);
        }

        // 更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }
    /**
     * 再来一单
     *
     * @param id
     */
    public void repetition(Long id) {
        // 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        // 根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里面的药品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        // 将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 订单搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 部分订单状态，需要额外返回订单药品信息，将Orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(), orderVOList);
    }

    private List<OrderVO> getOrderVOList(Page<Orders> page) {
        // 需要返回订单药品信息，自定义OrderVO响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if (!CollectionUtils.isEmpty(ordersList)) {
            for (Orders orders : ordersList) {
                // 将共同字段复制到OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                String orderItems = getOrderItemsStr(orders);

                // 将订单药品信息封装到orderVO中，并添加到orderVOList
                orderVO.setOrderItems(orderItems);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    /**
     * 根据订单id获取药品信息字符串
     *
     * @param orders
     * @return
     */
    private String getOrderItemsStr(Orders orders) {
        // 查询订单药品详情信息（订单中的药品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将每一条订单药品信息拼接为字符串（格式：阿莫西林胶囊*3；）
        List<String> orderDrugList = orderDetailList.stream().map(x -> {
            String orderDrug = x.getName() + "*" + x.getNumber() + ";";
            return orderDrug;
        }).collect(Collectors.toList());

        // 将该订单对应的所有药品信息拼接在一起
        return String.join("", orderDrugList);
    }

    /**
     * 各个状态的订单数量统计
     *
     * @return
     */
    public OrderStatisticsVO statistics() {
        // 根据状态，分别查询出待接单、待派送、派送中的订单数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        // 将查询出的数据封装到orderStatisticsVO中响应
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();

        orderMapper.update(orders);
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());

        // 订单只有存在且状态为2（待接单）才可以拒单
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (Orders.PAID.equals(payStatus)) {
            //用户已支付，需要退款
            String refund = weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    ordersDB.getAmount(),
                    ordersDB.getAmount());
            log.info("申请退款：{}", refund);

            //退款后支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);
        }

        // 拒单需要退款，根据订单id更新订单状态、拒单原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);
    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());

        Orders orders = new Orders();
        orders.setId(ordersCancelDTO.getId());

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (Orders.PAID.equals(payStatus)) {
            //用户已支付，需要退款
            String refund = weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    ordersDB.getAmount(),
                    ordersDB.getAmount());
            log.info("申请退款：{}", refund);

            //退款后支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);
        }

        // 管理端取消订单需要退款，根据订单id更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 派送订单
     *
     * @param id
     */
    public void delivery(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为3
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为派送中
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders);
    }

    /**
     * 完成订单
     *
     * @param id
     */
    public void complete(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为4
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为完成
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders);
    }
    /**
     * 拆 Feign 返回的 Result：调用失败抛业务异常（阶段 5 fail-fast，暂无降级）。
     * data 为 null 时**不抛**，交由调用方判断（如下单时的地址簿空校验）。
     */
    private <T> T unwrap(Result<T> result, String action) {
        if (result == null || result.getCode() == null || result.getCode() != 1) {
            throw new OrderBusinessException(action + "失败：" + (result == null ? "无响应" : result.getMsg()));
        }
        return result.getData();
    }

    /**
     * 订单催单
     *
     * @param id
     */
    public void reminder(long id) {
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，
        if (ordersDB == null ) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Map map = new HashMap();
        map.put("type",2);//1表示来单提醒，2表示订单催单
        map.put("orderId",id);
        map.put("content","订单号："+ordersDB.getNumber());
        String json = JSON.toJSONString(map);

        wsPushProducer.push(json);
    }
}
