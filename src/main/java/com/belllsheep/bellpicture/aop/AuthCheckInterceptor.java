package com.belllsheep.bellpicture.aop;

import com.belllsheep.bellpicture.annotation.AuthCheck;
import com.belllsheep.bellpicture.exception.ErrorCode;
import com.belllsheep.bellpicture.exception.ThrowUtils;
import com.belllsheep.bellpicture.model.enums.UserRoleEnum;
import com.belllsheep.bellpicture.model.vo.UserVo;
import com.belllsheep.bellpicture.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuthCheckInterceptor {

    @Resource
    private UserService userService;
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        RequestAttributes attributes=RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request= ((ServletRequestAttributes)attributes).getRequest();

        //获取当前切入点方法需要的权限
        UserRoleEnum mustRoleEnum=UserRoleEnum.getEnumByKey(mustRole);

        //不需要权限，直接放行
        if(mustRoleEnum==null){
            return joinPoint.proceed();
        }

        //需要登录状态
        UserVo loginUser=userService.getCurrentUser(request);

        if(loginUser == null){
            ThrowUtils.throwIf(true, ErrorCode.NOT_LOGIN_ERROR);
        }

        //需要管理员权限
        if(mustRoleEnum == UserRoleEnum.ADMIN){
            ThrowUtils.throwIf(!loginUser.getUserRole().equals(
                UserRoleEnum.ADMIN.getValue()),
                ErrorCode.NO_AUTH_ERROR,
                "需要管理员权限"
            );
        }

        return joinPoint.proceed();
    }
}
