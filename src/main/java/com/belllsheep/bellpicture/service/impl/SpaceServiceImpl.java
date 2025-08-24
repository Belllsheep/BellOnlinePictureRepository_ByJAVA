package com.belllsheep.bellpicture.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.belllsheep.bellpicture.exception.BusinessException;
import com.belllsheep.bellpicture.exception.ErrorCode;
import com.belllsheep.bellpicture.exception.ThrowUtils;
import com.belllsheep.bellpicture.manager.sharding.DynamicShardingManager;
import com.belllsheep.bellpicture.model.dto.space.SpaceAddRequest;
import com.belllsheep.bellpicture.model.dto.space.SpaceQueryRequest;
import com.belllsheep.bellpicture.model.entity.Space;
import com.belllsheep.bellpicture.model.entity.SpaceUser;
import com.belllsheep.bellpicture.model.entity.User;
import com.belllsheep.bellpicture.model.enums.SpaceLevelEnum;
import com.belllsheep.bellpicture.model.enums.SpaceRoleEnum;
import com.belllsheep.bellpicture.model.enums.SpaceTypeEnum;
import com.belllsheep.bellpicture.model.vo.SpaceVo;
import com.belllsheep.bellpicture.service.SpaceService;
import com.belllsheep.bellpicture.mapper.SpaceMapper;
import com.belllsheep.bellpicture.service.SpaceUserService;
import com.belllsheep.bellpicture.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author 何欣
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2025-07-29 10:37:24
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{

    @Resource
    UserService userService;
    @Resource
    TransactionTemplate transactionTemplate;
    @Resource
    SpaceUserService spaceUserService;
    @Resource
    @Lazy
    private DynamicShardingManager dynamicShardingManager;

    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);
        // 要创建
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
            if (spaceType == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类别不能为空");
            }
        }
        // 修改数据时，如果要改空间级别
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
        if (spaceType != null && spaceTypeEnum == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
    }


    @Override
    public SpaceVo obj2Vo(Space space) {
        if (space == null) {
            return null;
        }
        SpaceVo spaceVo = SpaceVo.obj2Vo(space);
        User user = userService.getById(space.getUserId());
        spaceVo.setUser(userService.obj2UserVo( user));
        return spaceVo;
    }

    @Override
    public Page<SpaceVo> spacePage2SpaceVoPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVo> spaceVoPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVoPage;
        }

        // 对象列表 => 封装对象列表
        List<SpaceVo> spaceVOList = spaceList.stream().map(SpaceVo::obj2Vo).collect(Collectors.toList());
        //搜索所有相关用户，将其填充到对应的PictureVo中
        // 1. 关联查询用户信息
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        spaceVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.obj2UserVo(user));
        });
        spaceVoPage.setRecords(spaceVOList);
        return spaceVoPage;
    }

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if(spaceQueryRequest!= null){
            Long id = spaceQueryRequest.getId();
            String spaceName = spaceQueryRequest.getSpaceName();
            Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
            Long userId = spaceQueryRequest.getUserId();
            String sortOrder = spaceQueryRequest.getSortOrder();
            String sortField = spaceQueryRequest.getSortField();
            Integer spaceType = spaceQueryRequest.getSpaceType();

            queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
            queryWrapper.eq(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
            queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
            queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
            queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);
            //排序
            queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        }

        return queryWrapper;
    }

    /**
     * 根据空间等级填充默认空间信息
     * maxSize maxCount
     * @param space
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }

    /**
     * 创建空间
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {

        //填充默认参数,默认创建普通空间
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        if(StrUtil.isBlank(space.getSpaceName())){
            space.setSpaceName("默认空间");
        }
        if(space.getSpaceLevel() == null){
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if(space.getSpaceType() == null){
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        this.fillSpaceBySpaceLevel(space);

        //参数校验
        this.validSpace(space, true);

        //校验空间合法性（权限等等）
        Long userId = loginUser.getId();
        space.setUserId(userId);
        //普通用户不能创建高级空间
        if(!space.getSpaceLevel().equals(SpaceLevelEnum.COMMON.getValue()) && !userService.isAdmin(loginUser)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"无权限创建该级别的空间");
        }

        //控制一个用户只能创建一个私有空间
        //TODO 拓展：使用concurrentHashMap优化，防止内存泄露
        String lock=String.valueOf(userId).intern();
        synchronized (lock){
            Long spaceId =transactionTemplate.execute(status -> {
                //判断该用户是否已有空间
                boolean exist = this.lambdaQuery()
                        .eq(Space::getUserId, userId)
                        .eq(Space::getSpaceType,space.getSpaceType())
                        .exists();
                //如果有，不能创建
                log.error("申请空间类别："+space.getSpaceType());
                ThrowUtils.throwIf(exist,ErrorCode.OPERATION_ERROR,"每个用户每类空间只能创建一个");

                //创建
                boolean ok=this.save(space);
                ThrowUtils.throwIf(!ok,ErrorCode.OPERATION_ERROR);

                // 如果是团队空间，关联新增团队成员记录
                if (SpaceTypeEnum.TEAM.getValue() == spaceAddRequest.getSpaceType()) {
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId());
                    spaceUser.setUserId(userId);
                    spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                    ok = spaceUserService.save(spaceUser);
                    ThrowUtils.throwIf(!ok, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
                }
                // 创建分表
                //dynamicShardingManager.createSpacePictureTable(space);
                // 返回新写入的数据 id
                return space.getId();

            });
            return Optional.ofNullable(spaceId).orElse( - 1L);
        }

    }

    /**
     * 空间权限校验
     * @param user
     * @param space
     * @return
     *
     * 仅管理员和空间创建者有权限
     */
    @Override
    public void checkSpaceAuth(User user, Space space) {
        if(!user.getId().equals(space.getUserId()) && !userService.isAdmin(user)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"无空间访问权限");
        }
    }
}




