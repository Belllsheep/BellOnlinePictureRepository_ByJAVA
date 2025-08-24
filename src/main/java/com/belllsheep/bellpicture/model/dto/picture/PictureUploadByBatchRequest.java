package com.belllsheep.bellpicture.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 批量导入图片请求
 */
@Data
public class PictureUploadByBatchRequest implements Serializable {
    private static final long serialVersionUID = 2645455820221901382L;
    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 抓取数量
     */
    private Integer count = 10;

    /**
     * 名称前缀
     */
    private String namePrefix;



}
