package com.belllsheep.bellpicture.model.vo;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.belllsheep.bellpicture.model.entity.Picture;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class PictureVo implements Serializable {
    private static final long serialVersionUID = 138672783723200164L;
    /**
     * id
     */
    private Long id;

    /**
     * 图片 url
     */
    private String url;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签（JSON 数组）
     */
    private List<String> tags;

    /**
     * 图片体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 图片宽高比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 创建用户UsrVo
     * Picture中没有的字段
     */
    private UserVo user;

    /**
     * 缩略图 url
     */
    private String thumbnailUrl;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 图片主色调
     */
    private String picColor;

    /**
     * 权限列表
     */
    private List<String> permissionList = new ArrayList<>();




//    /**
//     * 自定义setId
//     */
//    public void setId(Long id) {
//        this.id = id.toString();
//    }
//    /**
//     * 自定义getId
//     */
//    public Long getId() {
//        return Long.parseLong(this.id);
//    }

    /**
     * Picture对象转Vo
     * 不填充UserVo
     * @param picture
     * @return
     */
    public static PictureVo obj2Vo(Picture  picture){
        if(picture==null)
            return null;

        PictureVo pictureVo = new PictureVo();
        BeanUtil.copyProperties(picture, pictureVo);
        //设置tags列表
        pictureVo.setTags(JSONUtil.toList(picture.getTags(), String.class));
        return pictureVo;
    }

    /**
     * Vo对象转Picture
     * @param pictureVo
     * @return
     */
    public static PictureVo vo2Obj(PictureVo  pictureVo){
        if(pictureVo==null)
            return null;

        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureVo, picture);
        //设置tags列表
        pictureVo.setTags(JSONUtil.toList(picture.getTags(), String.class));
        return pictureVo;
    }

}
