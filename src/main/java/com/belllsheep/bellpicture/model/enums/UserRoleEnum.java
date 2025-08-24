package com.belllsheep.bellpicture.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum UserRoleEnum {

    USER("user", "用户"),
    ADMIN("admin", "管理员");

    private  final String key;
    private  final String value;

    UserRoleEnum(String key, String value){
        this.key = key;
        this.value = value;
    }

    public static UserRoleEnum getEnumByKey(String key){
        if(ObjUtil.isEmpty( key)){
            return null;
        }
        for (UserRoleEnum entry : UserRoleEnum.values()) {
            if (ObjUtil.equal(entry.getKey(), key)) {
                return entry;
            }
        }
        return null;
    }
}
