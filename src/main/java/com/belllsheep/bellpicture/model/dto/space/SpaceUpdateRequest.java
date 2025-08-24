package com.belllsheep.bellpicture.model.dto.space;


import lombok.Data;

import java.io.Serializable;

/**
 * 空间更新请求
 * 管理员专用请求
 */
@Data
public class SpaceUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1440263020551125526L;
    /**
     * 空间id
     */
    private Long id;
    /**
     * 空间名称
     */
    private String spaceName;
    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;
    /**
     * 空间最大容量
     */
    private Long maxSize;
    /**
     * 空间最大文件数量
     */
    private Long maxCount;

}
