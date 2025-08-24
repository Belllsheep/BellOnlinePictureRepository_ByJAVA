package com.belllsheep.bellpicture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.belllsheep.bellpicture.model.dto.spaceuser.SpaceUserAddRequest;
import com.belllsheep.bellpicture.model.dto.spaceuser.SpaceUserQueryRequest;
import com.belllsheep.bellpicture.model.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.belllsheep.bellpicture.model.vo.SpaceUserVo;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author 何欣
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2025-08-19 14:29:49
*/
public interface SpaceUserService extends IService<SpaceUser> {

    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    void validSpaceUser(SpaceUser spaceUser, boolean add);

    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);


    SpaceUserVo obj2Vo(SpaceUser spaceUser);

    List<SpaceUserVo> objList2VoList(List<SpaceUser> spaceUserList);
}
