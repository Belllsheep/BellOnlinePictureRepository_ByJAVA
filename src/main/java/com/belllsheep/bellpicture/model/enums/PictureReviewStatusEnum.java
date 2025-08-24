package com.belllsheep.bellpicture.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum PictureReviewStatusEnum {

    REVIEWING("待审核", 0),
    PASS("通过", 1),
    REJECT("拒绝", 2);

    private  final String key;
    private  final int value;

    PictureReviewStatusEnum(String key, int value){
        this.key = key;
        this.value = value;
    }

    /**
     * 根据key获取枚举值
     * @param key
     * @return
     */
    public static PictureReviewStatusEnum getEnumByKey(String key){
        if(ObjUtil.isEmpty( key)){
            return null;
        }
        for (PictureReviewStatusEnum entry : PictureReviewStatusEnum.values()) {
            if (ObjUtil.equal(entry.getKey(), key)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 根据value获取枚举常量
     * @param value
     * @return
     */
    public static PictureReviewStatusEnum getEnumByValue(Integer value){
        if(ObjUtil.isEmpty(value)){
            return null;
        }
        for (PictureReviewStatusEnum entry : PictureReviewStatusEnum.values()) {
            if (ObjUtil.equal(entry.getValue(), value)) {
                return entry;
            }
        }
        return null;
    }
}
