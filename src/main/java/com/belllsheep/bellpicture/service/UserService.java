package com.belllsheep.bellpicture.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.belllsheep.bellpicture.model.dto.user.UserLoginRequest;
import com.belllsheep.bellpicture.model.dto.user.UserQueryRequest;
import com.belllsheep.bellpicture.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.belllsheep.bellpicture.model.vo.UserLoginVo;
import com.belllsheep.bellpicture.model.vo.UserVo;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author 何欣
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2025-07-21 16:39:47
*/
public interface UserService extends IService<User> {

    /**
     * 用户注册
     * @param userAccount 用户账号
     * @param userPassword 用户密码
     * @param checkPassword 确认密码
     * @return 新用户id
     */
    Long userRegister(String userAccount, String userPassword, String checkPassword);


    String getEncryptPassword(String userPassword);

    /**
     * 登录
     * @param userLoginRequest
     * @param request
     * @return
     */
    UserLoginVo userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 获取当前线程用户
     * 即获得当前request对应session中的用户
     * @param request
     * @return
     */
    UserVo getCurrentUser(HttpServletRequest request);


    /**
     * 退出登录
     * @param request
     * @return
     */
    boolean UserLogout(HttpServletRequest request);

    /**
     * 分页查询用户列表
     */
    Page<UserVo> queryUserList(UserQueryRequest userQueryRequest, HttpServletRequest request);

    List<UserVo> getUserVOList(List<User> userList);


    UserVo obj2UserVo(User user);

    boolean resetPasswordById(Long userId);

    User vo2obj(UserVo userVo);

    /**
     * 判断是否为管理员
     * @param user
     * @return
     */
    boolean isAdmin(User user);

    boolean isAdmin(UserVo user);

}
