package com.belllsheep.bellpicture.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间修改请求
 * 普通用户请求：只能修改空间名称
 */
@Data
public class SpaceEditRequest implements Serializable {
    /**
     * 空间id
     */
    private Long id;
    /**
     * 空间名称
     */
    private String spaceName;
}
