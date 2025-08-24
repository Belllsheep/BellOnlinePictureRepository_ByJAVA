package com.belllsheep.bellpicture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.belllsheep.bellpicture.model.dto.space.SpaceAddRequest;
import com.belllsheep.bellpicture.model.dto.space.SpaceQueryRequest;
import com.belllsheep.bellpicture.model.entity.Space;
import com.belllsheep.bellpicture.model.entity.User;
import com.belllsheep.bellpicture.model.vo.SpaceVo;


import javax.servlet.http.HttpServletRequest;

/**
* @author 何欣
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-07-29 10:37:24
*/
public interface SpaceService extends IService<Space> {
    void validSpace(Space space, boolean add);

    SpaceVo obj2Vo(Space space);

    Page<SpaceVo> spacePage2SpaceVoPage(Page<Space> spacePage, HttpServletRequest request);

    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 根据空间等级填充默认空间信息
     * maxSize maxCount
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 创建空间
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    void checkSpaceAuth(User user, Space space);
}
