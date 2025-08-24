package com.belllsheep.bellpicture.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.belllsheep.bellpicture.exception.ErrorCode;
import com.belllsheep.bellpicture.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 文件上传
 * 通过MultipartFile进行文件上传
 */
@Service
public class FilePictureUpload extends PictureUploadTemplate{
    /**
     * 将inputSource转换成MultipartFile
     * @param inputSource
     * @return
     */
    private MultipartFile getMultipartFile(Object inputSource) {
        // 检查并确保 inputSource 是 MultipartFile 类型
        ThrowUtils.throwIf(!(inputSource instanceof MultipartFile),
                ErrorCode.PARAMS_ERROR,
                "参数类型错误：需要 MultipartFile 类型");
        return (MultipartFile) inputSource; // 类型转换已通过 instanceof 验证
    }
    /**
     *
     * @param inputSource
     * @param file
     */
    @Override
    protected void processFile(Object inputSource, File file) {
        MultipartFile multipartFile=this.getMultipartFile(inputSource);
        try {
            multipartFile.transferTo(file);
        } catch (IOException e) {
            ThrowUtils.throwIf(true, ErrorCode.SYSTEM_ERROR, "文件上传失败");
        }
    }

    @Override
    protected String getoriginFileName(Object inputSource) {
        MultipartFile multipartFile=this.getMultipartFile(inputSource);
        return multipartFile.getOriginalFilename();
    }

    @Override
    protected void validPicture(Object inputSource) {
        MultipartFile multipartFile=this.getMultipartFile(inputSource);
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
}
