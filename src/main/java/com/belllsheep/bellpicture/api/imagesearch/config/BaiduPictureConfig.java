package com.belllsheep.bellpicture.api.imagesearch.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
@ConfigurationProperties(prefix = "baidu-picture")
public class BaiduPictureConfig {
    /**
     * 百度图片访问的 ACS 密钥
     */
    private String acsAccess;
}

