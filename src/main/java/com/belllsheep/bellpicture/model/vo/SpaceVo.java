package com.belllsheep.bellpicture.model.vo;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.belllsheep.bellpicture.model.entity.Space;
import com.belllsheep.bellpicture.model.entity.User;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class SpaceVo implements Serializable {
    /**
     * id
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
     * 空间类型：0-个人空间 1-团队空间
     */
    private Integer spaceType;

    /**
     * 空间图片的最大总大小
     */
    private Long maxSize;

    /**
     * 空间图片的最大数量
     */
    private Long maxCount;

    /**
     * 当前空间下图片的总大小
     */
    private Long totalSize;

    /**
     * 当前空间下的图片数量
     */
    private Long totalCount;

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
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建用户信息
     */
    private UserVo user;

    /**
     * 权限列表
     */
    private List<String> permissionList = new ArrayList<>();


    /**
     * 对象转换成vo
     * 不填充UserVo user字段
     * @param obj
     * @return
     */
    public static SpaceVo obj2Vo(Space obj) {
        if (obj == null) {
            return null;
        }
        SpaceVo vo = new SpaceVo();
        BeanUtil.copyProperties(obj, vo);
        return  vo;
    }

    /**
     * Vo转换成对象
     * @param vo
     * @return
     */
    public static Space Vo2obj(SpaceVo vo) {
        if (vo == null) {
            return null;
        }
        Space obj = new Space();
        BeanUtil.copyProperties(vo, obj);
        return  obj;
    }

}
