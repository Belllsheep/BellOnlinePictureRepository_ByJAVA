package com.belllsheep.bellpicture.model.dto.file;

import lombok.Data;

@Data
public class UploadPictureResult {

    /**
     * 图片地址
     */
    private String url;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 文件体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private int picWidth;

    /**
     * 图片高度
     */
    private int picHeight;

    /**
     * 图片宽高比
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    /**
     * 缩略图 url
     */
    private String thumbnailUrl;
    /**
     * 图片主色调
     */
    private String picColor;


}


//@Data
//public class UploadPictureResult {
//    /**
//     * 图片格式
//     */
//    private  String format ;
//    /**
//     * 图片宽度
//     */
//    private Integer width;
//    /**
//     * 图片高度
//     */
//    private  Integer height;
//    /**
//     * 图片质量
//     */
//    private  Integer quality;
//    /**
//     * 图片主色调
//     */
//    private  String ave;
//    /**
//     * 图片旋转角度
//     */
//    private  String orientation;
//}
