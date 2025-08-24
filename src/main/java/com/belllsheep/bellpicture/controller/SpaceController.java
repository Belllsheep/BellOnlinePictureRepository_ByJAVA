package com.belllsheep.bellpicture.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.belllsheep.bellpicture.annotation.AuthCheck;
import com.belllsheep.bellpicture.common.BaseResponse;
import com.belllsheep.bellpicture.common.ObjKeyUtils;
import com.belllsheep.bellpicture.common.ResultUtils;
import com.belllsheep.bellpicture.constant.UserConstant;
import com.belllsheep.bellpicture.exception.BusinessException;
import com.belllsheep.bellpicture.exception.ErrorCode;
import com.belllsheep.bellpicture.exception.ThrowUtils;
import com.belllsheep.bellpicture.manager.auth.SpaceUserAuthManager;
import com.belllsheep.bellpicture.model.dto.DeleteRequest;
import com.belllsheep.bellpicture.model.dto.space.*;
import com.belllsheep.bellpicture.model.entity.Space;
import com.belllsheep.bellpicture.model.entity.User;
import com.belllsheep.bellpicture.model.enums.UserRoleEnum;
import com.belllsheep.bellpicture.model.vo.SpaceVo;
import com.belllsheep.bellpicture.model.vo.UserVo;
import com.belllsheep.bellpicture.service.SpaceService;
import com.belllsheep.bellpicture.service.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;

//@Api(tags = "空间服务")
@Slf4j
@RestController
@RequestMapping("/space")
public class SpaceController {
    @Resource
    private SpaceService spaceService;
    @Resource
    private UserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;


    /**
     * 删除空间
     *本人或者管理员可以删除
     * @param deleteRequest
     * @param request
     * @return
     */
    @ApiOperation("删除空间")
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_USER)
    public BaseResponse deleteSpace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null, ErrorCode.PARAMS_ERROR);
        Long deleteId = deleteRequest.getId();
        if (deleteId == null || deleteId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 查询空间（判断是否存在；判断是否有删除权限）
        Space space = spaceService.getById(deleteId);
        ThrowUtils.throwIf(space==null,ErrorCode.NOT_FOUND_ERROR,"空间不存在");
        Long userId = userService.getCurrentUser(request).getId();
        String userRole = userService.getCurrentUser(request).getUserRole();
        Long spaceOwnerId = space.getUserId();
        ThrowUtils.throwIf(!spaceOwnerId.equals(userId) || !userRole.equals(UserRoleEnum.ADMIN.getValue()), ErrorCode.NO_AUTH_ERROR,"没有删除权限");

        // 删除空间
        boolean result = spaceService.removeById(deleteId);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除失败");
        return ResultUtils.success();
    }

    /**
     * 更新空间
     * 仅管理员可用
     */
    @ApiOperation("更新空间（管理员）")
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_ADMIN)
    public BaseResponse updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUpdateRequest == null || spaceUpdateRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        //判断空间是否存在
        Space oldspace = spaceService.getById(spaceUpdateRequest.getId());
        ThrowUtils.throwIf(oldspace == null, ErrorCode.NOT_FOUND_ERROR, "更新空间（管理员）空间不存在");

        //更新数据
        Space space = new Space();
        BeanUtil.copyProperties(spaceUpdateRequest, space);

        //校验更新的合法性
        spaceService.validSpace(space, false);

        //补充填充空间权限信息
        spaceService.fillSpaceBySpaceLevel(space);

        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "空间更新失败");
        return ResultUtils.success();
    }

    /**
     * 根据 id 获取空间（仅管理员可用）
     */
    @ApiOperation("获取空间")
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_ADMIN)
    public BaseResponse<Space> getSpaceById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(space);
    }

    /**
     * 根据 id 获取空间（封装类）
     */
    @ApiOperation("获取空间（封装类）")
    @GetMapping("/get/vo")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_ADMIN)
    public BaseResponse<SpaceVo> getSpaceVOById(long id, HttpServletRequest request) {
        //TODO 仅本人能获取
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        //填充权限列表
        User loginUser=userService.vo2obj(userService.getCurrentUser(request));
        List< String> permissionList = spaceUserAuthManager.getPermissionList(space,loginUser);
        SpaceVo result = spaceService.obj2Vo(space);
        result.setPermissionList(permissionList);
        // 获取封装类
        return ResultUtils.success(result);
    }

    /**
     * 分页获取空间列表（仅管理员可用）
     */
    @ApiOperation("分页获取空间列表")
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_ADMIN)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        // 4. 打印详细分页信息（关键！）
        log.info("分页结果: 总记录数={}, 当前页={}, 每页大小={}, 当前页记录数={}",
                spacePage.getTotal(),
                spacePage.getCurrent(),
                spacePage.getSize(),
                spacePage.getRecords().size());
        return ResultUtils.success(spacePage);
    }
    /**
     * 分页获取空间列表（封装类）
     * 需要过滤未过审数据
     */
    @ApiOperation("获取空间列表（封装类）")
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVo>> listSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest, HttpServletRequest request) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        // 获取封装类
        return ResultUtils.success(spaceService.spacePage2SpaceVoPage(spacePage, request));
    }


    /**
     * 编辑空间（给用户使用）
     */
    @ApiOperation("用户编辑空间")
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest, HttpServletRequest request) {
        if (spaceEditRequest == null || spaceEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);
        // 设置编辑时间
        space.setEditTime(new Date());
        // 数据校验
        spaceService.validSpace(space,false);
        UserVo loginUser = userService.getCurrentUser(request);
        // 判断是否存在
        long id = spaceEditRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldSpace.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        //补充填充审核参数
        spaceService.fillSpaceBySpaceLevel(space);

        // 操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }



    /**
     * 创建空间（给用户使用）
     */
    @ApiOperation("用户添加空间")
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_USER)
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.vo2obj(userService.getCurrentUser(request));
        long newId = spaceService.addSpace(spaceAddRequest, loginUser);
        return ResultUtils.success(newId);
    }


}
