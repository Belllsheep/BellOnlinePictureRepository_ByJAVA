package com.belllsheep.bellpicture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.belllsheep.bellpicture.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.belllsheep.bellpicture.model.dto.picture.*;
import com.belllsheep.bellpicture.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.belllsheep.bellpicture.model.entity.User;
import com.belllsheep.bellpicture.model.vo.PictureVo;
import com.belllsheep.bellpicture.model.vo.UserVo;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author 何欣
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-07-24 15:10:24
*/
public interface PictureService extends IService<Picture> {

    PictureVo uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * 获取条件查询器
     *
     * @param pictureQueryRequest
     * @return
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * Picture对象转PictureVo
     * @param picture
     * @return
     */
    PictureVo obj2Vo(Picture picture);

    /**
     *分页获取图片封装
     * picturePage转换成PictureVoPage
     * @param picturePage
     * @param request
     * @return
     */
    Page<PictureVo> picturePage2PictureVoPage(Page<Picture> picturePage, HttpServletRequest request);


    /**
     * 校验图片合法性
     * @param picture
     */
    void validPicture(Picture picture);

    /**
     * 审核图片
     * @param pictureReviewRequest
     * @param loginUser 审核员
     */
    void doReviewPicture(PictureReviewRequest pictureReviewRequest, UserVo loginUser);

    void fillReviewParam(Picture picture, UserVo loginUser);

    void fillReviewParam(Picture picture, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);


    /**
     * 清理云COS上的图片
     * @param oldPicture
     */
    @Async
    void clearPictureFile(Picture oldPicture);

    void checkPictureAuth(User loginUser, Picture picture);

    List<PictureVo> searchPictureByColor(Long spaceId, String picColor, User loginUser);


    @Transactional(rollbackFor = Exception.class)
    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser);
}
