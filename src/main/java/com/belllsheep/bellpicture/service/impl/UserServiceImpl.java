package com.belllsheep.bellpicture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.belllsheep.bellpicture.constant.UserConstant;
import com.belllsheep.bellpicture.exception.BusinessException;
import com.belllsheep.bellpicture.exception.ErrorCode;
import com.belllsheep.bellpicture.exception.ThrowUtils;
import com.belllsheep.bellpicture.manager.auth.StpKit;
import com.belllsheep.bellpicture.model.dto.user.UserLoginRequest;
import com.belllsheep.bellpicture.model.dto.user.UserQueryRequest;
import com.belllsheep.bellpicture.model.entity.User;
import com.belllsheep.bellpicture.model.enums.UserRoleEnum;
import com.belllsheep.bellpicture.model.vo.UserLoginVo;
import com.belllsheep.bellpicture.model.vo.UserVo;
import com.belllsheep.bellpicture.service.UserService;
import com.belllsheep.bellpicture.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author 何欣
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2025-07-21 16:39:47
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Override
    public Long userRegister(String userAccount, String userPassword, String checkPassword) {
        //1.异常判断
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if(!userPassword.equals(checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不一致");
        }

        //2.判断数据库中是否已存在账户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.baseMapper.selectCount(queryWrapper);
        if(count > 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账户已存在,请重新输入账户");
        }

        //3.创建用户
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(getEncryptPassword(userPassword));
        user.setUserName("新用户");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean result = this.save(user);
        if(!result){
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "注册失败,数据库错误");
        }
        //4.返回用户id
        return user.getId();
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "BelllSheep_is_the_Greatest";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    @Override
    public UserLoginVo userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request){
        //1.异常处理
        ThrowUtils.throwIf(userLoginRequest==null,ErrorCode.PARAMS_ERROR,"参数为空");
        //2.检查密码,查询用户并返回
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userLoginRequest.getUserAccount());
        queryWrapper.eq("userPassword", getEncryptPassword(userLoginRequest.getUserPassword()));
        User user = this.baseMapper.selectOne(queryWrapper);
        if(user==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账户不存在或者密码错误");
        }

        //3.封装数据
        UserLoginVo userLoginVo = new UserLoginVo();
        BeanUtil.copyProperties(user, userLoginVo);

        //4.将登录信息保存到session中
        request.getSession().setAttribute(UserConstant.USER_LOGIN_INFO, obj2UserVo(user));

        //5.记录用户登录态到 Sa-token，便于空间鉴权时使用，注意保证该用户信息与 SpringSession 中的信息过期时间一致
        try {
            StpKit.SPACE.login(user.getId());
            StpKit.SPACE.getSession().set(UserConstant.USER_LOGIN_INFO, user);
        } catch (Exception e) {
            log.error("鉴权失败");
        }

        return userLoginVo;
    }

    @Override
    public UserVo getCurrentUser(HttpServletRequest request) {
        //获取session
         UserVo userVo= (UserVo)request.getSession().getAttribute(UserConstant.USER_LOGIN_INFO);
        ThrowUtils.throwIf(userVo==null,ErrorCode.NOT_LOGIN_ERROR);
        return userVo;
    }

    @Override
    public boolean UserLogout(HttpServletRequest request) {
        UserVo userVo= (UserVo)request.getSession().getAttribute(UserConstant.USER_LOGIN_INFO);
        ThrowUtils.throwIf(userVo==null,ErrorCode.OPERATION_ERROR,"用户未登录");

        //移除登录状态
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_INFO);
        return true;
    }

    @Override
    public Page<UserVo> queryUserList(UserQueryRequest userQueryRequest, HttpServletRequest request) {

        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();

        //异常处理：无条件查询
        if(userQueryRequest== null){
            userQueryRequest=new UserQueryRequest();
        }

        //将查询参数转为查询条件
        QueryWrapper<User> queryWrapper = this.getQueryWrapper(userQueryRequest);

        //查询
        Page<User> userPage = this.page(new Page<>(current, pageSize),queryWrapper);

        //数据转换
        Page<UserVo> userVoPage = new Page<>(current, pageSize, userPage.getTotal());
        List<UserVo> userVOList =this.getUserVOList(userPage.getRecords());
        userVoPage.setRecords(userVOList);

        return userVoPage;
    }

    @Override
    public List<UserVo> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::obj2UserVo).collect(Collectors.toList());
    }

    @Override
    public UserVo obj2UserVo(User user) {
        if (user == null) {
            return null;
        }
        UserVo userVO = new UserVo();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public boolean resetPasswordById(Long userId) {
        User user=new User();
        user.setUserPassword(getEncryptPassword("12345678"));
        this.update(user, new QueryWrapper<User>().eq("id",userId));
        return true;
    }

    @Override
    public User vo2obj(UserVo userVo) {
        User user=new User();
        BeanUtil.copyProperties(userVo, user);
        return user;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    @Override
    public boolean isAdmin(UserVo user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }


}




