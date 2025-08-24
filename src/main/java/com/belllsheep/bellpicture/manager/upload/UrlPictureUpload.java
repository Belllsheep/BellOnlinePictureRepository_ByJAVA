package com.belllsheep.bellpicture.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.belllsheep.bellpicture.exception.BusinessException;
import com.belllsheep.bellpicture.exception.ErrorCode;
import com.belllsheep.bellpicture.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * 通过url上传图片
 */
@Service
public class UrlPictureUpload extends PictureUploadTemplate {
    /**
     * 将inputSource转换成String
     * @param inputSource
     * @return
     */
    private String geStringFileUrl(Object inputSource) {
        // 检查并确保 inputSource 是 MultipartFile 类型
        ThrowUtils.throwIf(!(inputSource instanceof String),
                ErrorCode.PARAMS_ERROR,
                "参数类型错误：需要 MultipartFile 类型");
        return (String) inputSource; // 类型转换已通过 instanceof 验证
    }

    @Override
    protected void processFile(Object inputSource, File file) {
        String fileUrl = this.geStringFileUrl(inputSource);
        HttpUtil.downloadFile(fileUrl, file);
    }

    @Override
    protected String getoriginFileName(Object inputSource) {
        String fileUrl = this.geStringFileUrl(inputSource);
        return FileUtil.mainName(fileUrl);
    }

    /**
     * 校验文件
     * @param inputSource
     */
    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = this.geStringFileUrl(inputSource);
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
                final List<String> ALLOW_CONTENT_TYPES= Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
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
}

