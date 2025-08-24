package com.belllsheep.bellpicture.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Data;
import lombok.Getter;

@Getter
public enum SpaceLevelEnum {

    COMMON("普通版", 0, 100, 100L * 1024 * 1024),
    PROFESSIONAL("专业版", 1, 1000, 1000L * 1024 * 1024),
    FLAGSHIP("旗舰版", 2, 10000, 10000L * 1024 * 1024);

    /**
     * 空间等级中文名称
     */
    private final String text;
    /**
     * 空间等级对应值
     */
    private final int value;
    /**
     * 存储空间最大图片数量
     */
    private final long maxCount;

    /**
     * 存储空间最大图片总大小
     */
    private final long maxSize;

    /**
     * @param text 文本
     * @param value 值
     * @param maxSize 最大图片总大小
     * @param maxCount 最大图片总数量
     */
    SpaceLevelEnum(String text, int value, long maxCount, long maxSize) {
        this.text = text;
        this.value = value;
        this.maxCount = maxCount;
        this.maxSize = maxSize;
    }

    public static SpaceLevelEnum getEnumByValue(Integer value){
        if(ObjUtil.isEmpty(value)){
            return null;
        }
        for (SpaceLevelEnum entry : SpaceLevelEnum.values()) {
            if (entry.value==value) {
                return entry;
            }
        }
        return null;
    }
}
