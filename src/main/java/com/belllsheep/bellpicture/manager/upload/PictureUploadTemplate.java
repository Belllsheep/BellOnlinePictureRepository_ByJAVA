package com.belllsheep.bellpicture.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.http.HttpUtil;
import com.belllsheep.bellpicture.config.CosClientConfig;
import com.belllsheep.bellpicture.exception.BusinessException;
import com.belllsheep.bellpicture.exception.ErrorCode;
import com.belllsheep.bellpicture.manager.CosManager;
import com.belllsheep.bellpicture.model.dto.file.UploadPictureResult;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;


@Slf4j
public abstract class PictureUploadTemplate {
    protected static final int ONE_M = 1024*1024;

    @Resource
    protected CosManager cosManager;

    @Resource
    protected CosClientConfig cosClientConfig;

    /**
     * 模板方法，定义上传流程
     * inputSource：String fileUrl；
     *             or MultipartFile multipartFile
     */
    public final UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        //校验图片
        validPicture(inputSource);

        //生成云存储对象的图片路径
        String uuid = RandomUtil.randomString(16);
        //取文件名 （通过url）
        String originFileName =getoriginFileName(inputSource);
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originFileName));// 上传文件名：日期_uuid.后缀
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);// 上传路径：用户自定义上传路径前缀/日期_uuid.后缀

        //创建临时文件并上传到云存储
        File file = null;
        try {
            // 创建临时文件
            file = File.createTempFile(uploadPath, null);

            //获取临时文件
            processFile(inputSource,file);

            // 上传图片 并得到 返回结果（图片信息）
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            if (!CollUtil.isEmpty(objectList)) {
                CIObject compressedCIObject = objectList.get(0);
                CIObject thumbnailCIObject = objectList.get(1);
                // 封装压缩图返回结果
                return buildResult(originFileName, compressedCIObject, thumbnailCIObject, imageInfo);
            }
            // 封装原图返回结果
            return buildResult(imageInfo, originFileName, file, uploadPath);
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            deleteTempFile(file);
        }
    }

    /**
     * 获得临时文件，将临时文件放到file
     * @param inputSource
     * @param file
     */
    protected abstract void processFile(Object inputSource, File file);

    protected abstract String getoriginFileName(Object inputSource);

    protected abstract void validPicture(Object inputSource);

    /**
     * 封装返回结果
     * @param imageInfo
     * @param originFilename
     * @param file
     * @param uploadPath
     * @return
     */
    private UploadPictureResult buildResult(ImageInfo imageInfo, String originFilename, File file, String uploadPath) {
        // 封装返回结果（成 uploadPictureResult）
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        uploadPictureResult.setPicSize(FileUtil.size(file));
        uploadPictureResult.setPicColor(imageInfo.getAve());
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        return uploadPictureResult;
    }

    /**
     * 封装返回结果(压缩图)
     * @param originFilename
     * @param compressedCiObject
     * @return
     */
    private UploadPictureResult buildResult(String originFilename, CIObject compressedCiObject, CIObject thumbnailCiObject,ImageInfo imageInfo) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        uploadPictureResult.setPicColor(imageInfo.getAve());


        // 设置图片为压缩后的地址
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressedCiObject.getKey());
        // 设置图片缩略图的地址
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());
        return uploadPictureResult;
    }


    /**
     * 删除临时文件
     */
    public void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        // 删除临时文件
        boolean deleteResult = file.delete();
        if (!deleteResult) {
            log.error("file delete error, filepath = {}", file.getAbsolutePath());
        }
    }

}
