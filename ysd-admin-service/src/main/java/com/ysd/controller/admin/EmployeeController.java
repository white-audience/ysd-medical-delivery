package com.ysd.controller.admin;

import com.ysd.constant.JwtClaimsConstant;
import com.ysd.dto.EmployeeDTO;
import com.ysd.dto.EmployeeLoginDTO;
import com.ysd.dto.EmployeePageQueryDTO;
import com.ysd.entity.Employee;
import com.ysd.properties.JwtProperties;
import com.ysd.result.PageResult;
import com.ysd.result.Result;
import com.ysd.service.EmployeeService;
import com.ysd.utils.JwtUtil;
import com.ysd.vo.EmployeeLoginVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Tag(name = "员工相关接口")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @PostMapping("/login")
    @Operation(summary = "员工登录")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：username={}", employeeLoginDTO.getUsername());

        Employee employee = employeeService.login(employeeLoginDTO);

        //登录成功后，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .build();

        return Result.success(employeeLoginVO);
    }

    /**
     * 退出
     *
     * @return
     */
    @PostMapping("/logout")
    @Operation(summary = "员工退出")
    public Result<String> logout() {
        return Result.success();
    }


    @PostMapping
    @Operation(summary = "新增员工")
    public Result save(@RequestBody EmployeeDTO employeeDTO){
        log.info("新增员工：{}", employeeDTO);
        log.debug("当前线程的id：{}", Thread.currentThread().getId());
        employeeService.save(employeeDTO);
        return Result.success();
    }

    @GetMapping("/page")
    @Operation(summary = "员工分页查询")
    public Result<PageResult> page(EmployeePageQueryDTO employeePageQueryDTO){
        log.info("员工分页查询,参数为：{}",employeePageQueryDTO);
        PageResult pageResult = employeeService.pageQuery(employeePageQueryDTO);
        return Result.success(pageResult);
    }
    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     * @return
     */
    @PostMapping( "/status/{status}")
    @Operation(summary = "员工状态修改")
    public Result startOrStop(@PathVariable Integer status, Long id){
        log.info("员工状态修改：{},{}", status,id);
        employeeService.startOrStop(status,id);
        return  Result.success();
    }


    /*
    *根据id查询员工
    * @param id
    * @return
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据id查询员工信息")
    public Result<Employee> getById(@PathVariable Long id){
        Employee employee =employeeService.getById(id);
        return  Result.success(employee);
    }



    @PutMapping
    @Operation(summary = "编辑员工信息")
    public Result update(@RequestBody EmployeeDTO employeeDTO){
        log.info("员工信息修改：{}", employeeDTO);
        employeeService.update(employeeDTO);
        return Result.success();
    }
}
