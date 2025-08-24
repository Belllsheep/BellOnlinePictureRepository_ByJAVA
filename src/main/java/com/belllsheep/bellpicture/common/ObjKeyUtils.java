package com.belllsheep.bellpicture.common;

import cn.hutool.json.JSONUtil;
import org.springframework.util.DigestUtils;

/**
 * 获得对象缓存的 key
 * Obj -> key
 * 仅仅只是获取对象的到kry的映射，不包含前缀
 */
public class ObjKeyUtils {
    public static String getKeyByMd5(Object obj) {
        String objStr= JSONUtil.toJsonStr(obj);
        return DigestUtils.md5DigestAsHex(objStr.getBytes());
    }
}
