package com.belllsheep.bellpicture.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.belllsheep.bellpicture.annotation.AuthCheck;
import com.belllsheep.bellpicture.common.BaseResponse;
import com.belllsheep.bellpicture.common.ResultUtils;
import com.belllsheep.bellpicture.constant.UserConstant;
import com.belllsheep.bellpicture.exception.ErrorCode;
import com.belllsheep.bellpicture.exception.ThrowUtils;
import com.belllsheep.bellpicture.model.dto.user.UserDeleteRequest;
import com.belllsheep.bellpicture.model.dto.user.UserLoginRequest;
import com.belllsheep.bellpicture.model.dto.user.UserQueryRequest;
import com.belllsheep.bellpicture.model.dto.user.UserRegisterRequest;
import com.belllsheep.bellpicture.model.entity.User;
import com.belllsheep.bellpicture.model.vo.UserLoginVo;
import com.belllsheep.bellpicture.model.vo.UserVo;
import com.belllsheep.bellpicture.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

//@Api(tags = "用户服务")
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;
    /**
     * 用户注册接口
     * @return 新用户 id
     */
    @ApiOperation("用户注册")
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest){
        //异常处理
        ThrowUtils.throwIf(userRegisterRequest==null,ErrorCode.PARAMS_ERROR);
        long result=userService.userRegister(userRegisterRequest.getUserAccount(), userRegisterRequest.getUserPassword(), userRegisterRequest.getCheckPassword());
        return ResultUtils.success(result);
    }
    @ApiOperation("用户登录")
    @PostMapping("/login")
    public BaseResponse<UserLoginVo> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request){
        ThrowUtils.throwIf(userLoginRequest==null,ErrorCode.PARAMS_ERROR);
        UserLoginVo result=userService.userLogin(userLoginRequest,request);
        return ResultUtils.success(result);
    }

    @ApiOperation("获取登录信息")
    @GetMapping("get/login")
    public BaseResponse<UserVo> getLoginUser(HttpServletRequest request){
        UserVo result=userService.getCurrentUser(request);
        return ResultUtils.success(result);
    }

    @ApiOperation("用户登出")
    @GetMapping("/logout")
    public BaseResponse userLogout(HttpServletRequest request){
        boolean result=userService.UserLogout(request);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        return ResultUtils.success();
    }


    /**
     * 分页查询
     * @param userQueryRequest
     * @return
     */
    @ApiOperation("分页查询用户列表")
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_ADMIN)
    public BaseResponse<Page<UserVo>> listUserVoByPage(@RequestBody UserQueryRequest userQueryRequest,HttpServletRequest request) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<UserVo> result =userService.queryUserList(userQueryRequest,request);
        return ResultUtils.success(result);
    }

    /**
     * 删除用户
     */
    @ApiOperation("删除用户")
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_ADMIN)
    public BaseResponse deleteUserById(@RequestBody UserDeleteRequest userDeleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf((userDeleteRequest == null || userDeleteRequest.getId() <= 0), ErrorCode.PARAMS_ERROR);
        Long userId = userDeleteRequest.getId();
        boolean result = userService.removeById(userId);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除失败");

        return ResultUtils.success();
    }

    /**
     * 重置密码
     */
    @ApiOperation("重置密码")
    @PostMapping("/resetPassword")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_ADMIN)
    public BaseResponse resetPasswordById(@RequestBody UserDeleteRequest userRequest, HttpServletRequest request) {
        ThrowUtils.throwIf((userRequest == null || userRequest.getId() <= 0), ErrorCode.PARAMS_ERROR);
        Long userId = userRequest.getId();
        boolean result = userService.resetPasswordById(userId);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "重置密码失败");
        return ResultUtils.success();
    }

}
