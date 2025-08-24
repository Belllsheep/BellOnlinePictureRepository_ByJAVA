package com.belllsheep.bellpicture.model.dto.space;

import lombok.Data;

import java.io.Serializable;

@Data
public class SpaceAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 空间名称
     */
    private String spaceName;
    /**
     * 空间等级(0-普通版 1-专业版 2-旗舰版)
     */
    private Integer spaceLevel;

    /**
     * 空间类型：0-个人空间 1-团队空间
     */
    private Integer spaceType;

}
