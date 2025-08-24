package com.belllsheep.bellpicture.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.*;
import com.belllsheep.bellpicture.config.CosClientConfig;
import com.belllsheep.bellpicture.exception.BusinessException;
import com.belllsheep.bellpicture.exception.ErrorCode;
import com.belllsheep.bellpicture.exception.ThrowUtils;
import com.belllsheep.bellpicture.manager.upload.FilePictureUpload;
import com.belllsheep.bellpicture.manager.upload.UrlPictureUpload;
import com.belllsheep.bellpicture.model.dto.file.UploadPictureResult;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class FileManager {

    private static final int ONE_M = 1024*1024;
    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;


    /**
     * 上传图片
     *
     * @param multipartFile    文件
     * @param uploadPathPrefix 上传路径前缀
     * @return
     * @deprecated 改用FilePictureUpload
     */
    @Deprecated
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // 校验图片
        validPicture(multipartFile);

        // 图片上传地址：自己定义文件上传路径，原始文件名称可能导致访问路径有问题，所以使用uuid作为文件名
        String uuid = RandomUtil.randomString(16);
        String originFilename = multipartFile.getOriginalFilename();
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originFilename));// 上传文件名：日期_uuid.后缀
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);// 上传路径：用户自定义上传路径前缀/日期_uuid.后缀

        File file = null;
        try {
            // 创建临时文件
            file = File.createTempFile(uploadPath, null);
            multipartFile.transferTo(file);
            // 上传图片 并得到 返回结果（图片信息）
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
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
            uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
            return uploadPictureResult;
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            deleteTempFile(file);
        }
    }

    /**
     * 上传图片
     * 通过url
     *
     * @param fileUrl  文件资源路径
     * @param uploadPathPrefix 上传路径前缀
     * @return
     * @deprecated 改用UrlPictureUpload
     */
    @Deprecated
    public UploadPictureResult uploadPictureByUrl(String fileUrl, String uploadPathPrefix) {
        // 校验图片
        validPicture(fileUrl);
        //validPicture(multipartFile);

        // 图片上传地址：自己定义文件上传路径，原始文件名称可能导致访问路径有问题，所以使用uuid作为文件名
        String uuid = RandomUtil.randomString(16);
        //String originFilename = multipartFile.getOriginalFilename();

        String originFilename = FileUtil.mainName(fileUrl);

        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originFilename));// 上传文件名：日期_uuid.后缀
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);// 上传路径：用户自定义上传路径前缀/日期_uuid.后缀

        File file = null;
        try {
            // 创建临时文件
            file = File.createTempFile(uploadPath, null);
            //multipartFile.transferTo(file);
            HttpUtil.downloadFile(fileUrl, file);

            // 上传图片 并得到 返回结果（图片信息）
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
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
            uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
            return uploadPictureResult;
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            deleteTempFile(file);
        }
    }

    /**
     * 校验文件
     *
     * @param multipartFile multipart 文件
     */
    public void validPicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        // 1. 校验文件大小
        long fileSize = multipartFile.getSize();
        final long ONE_M = 1024 * 1024L;
        ThrowUtils.throwIf(fileSize > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");
        // 2. 校验文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        // 允许上传的文件后缀
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件类型错误");
    }

    /**
     * 校验文件
     * @param fileUrl
     */
    public void validPicture(String fileUrl) {
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址不能为空");

        //验证url合法性
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址不合法");
        }

        //校验url协议是否为 http/https
        ThrowUtils.throwIf(!(fileUrl.startsWith("http://") || fileUrl.startsWith("https://")), ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或者 HTTPS 协议的文件地址");

        //发送head请求以验证文件的存在性
        HttpResponse response = null;
        try{
            response = HttpUtil.createRequest(Method.HEAD,fileUrl).execute();

            //未正常返回
            if(response.getStatus()!= HttpStatus.HTTP_OK){
                return;
            }

            //正常返回，校验
            //校验文件类型
            String contentType = response.header("Content-Type");
            if(StrUtil.isNotBlank(contentType)){
                final  List<String> ALLOW_CONTENT_TYPES= Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType), ErrorCode.PARAMS_ERROR, "文件类型错误");
            }

            //校验文件大小
            long contentLength = response.contentLength();
            ThrowUtils.throwIf(contentLength > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");

        }finally {
            //关闭响应
            if(response!=null)
                response.close();
        }

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
