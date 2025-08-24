package com.belllsheep.bellpicture.controller;

import com.belllsheep.bellpicture.common.BaseResponse;
import com.belllsheep.bellpicture.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/")
public class MainController {

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public BaseResponse<String> health() {
        return ResultUtils.success("健康");
    }
}
