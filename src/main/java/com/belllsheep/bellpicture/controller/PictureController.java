package com.belllsheep.bellpicture.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.AbstractDb;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.belllsheep.bellpicture.annotation.AuthCheck;
import com.belllsheep.bellpicture.api.aliyunai.AliYunAiApi;
import com.belllsheep.bellpicture.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.belllsheep.bellpicture.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.belllsheep.bellpicture.api.imagesearch.ImageSearchApiFacade;
import com.belllsheep.bellpicture.api.imagesearch.model.ImageSearchResult;
import com.belllsheep.bellpicture.common.BaseResponse;
import com.belllsheep.bellpicture.common.ObjKeyUtils;
import com.belllsheep.bellpicture.common.ResultUtils;
import com.belllsheep.bellpicture.constant.UserConstant;
import com.belllsheep.bellpicture.exception.BusinessException;
import com.belllsheep.bellpicture.exception.ErrorCode;
import com.belllsheep.bellpicture.exception.ThrowUtils;
import com.belllsheep.bellpicture.manager.auth.SpaceUserAuthManager;
import com.belllsheep.bellpicture.manager.auth.StpKit;
import com.belllsheep.bellpicture.manager.auth.model.SpaceUserPermissionConstant;
import com.belllsheep.bellpicture.model.dto.picture.*;
import com.belllsheep.bellpicture.model.entity.Space;
import com.belllsheep.bellpicture.model.enums.PictureReviewStatusEnum;
import com.belllsheep.bellpicture.model.enums.SpaceLevelEnum;
import com.belllsheep.bellpicture.model.vo.PictureTagCategoryVo;
import com.belllsheep.bellpicture.model.entity.Picture;
import com.belllsheep.bellpicture.model.entity.User;
import com.belllsheep.bellpicture.model.enums.UserRoleEnum;
import com.belllsheep.bellpicture.model.vo.PictureVo;
import com.belllsheep.bellpicture.model.vo.SpaceLevel;
import com.belllsheep.bellpicture.model.vo.UserVo;
import com.belllsheep.bellpicture.service.PictureService;
import com.belllsheep.bellpicture.service.SpaceService;
import com.belllsheep.bellpicture.service.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

//@Api(tags = "图片服务")
@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {
    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SpaceService spaceService;

    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();
    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;


    /**
     * 上传图片
     * 开放用户使用后，要审核之后才能开放用户查看
     * @param multipartFile
     * @param pictureUploadRequest
     * @param request
     * @return
     */
    @ApiOperation("图片上传")
    @PostMapping("/upload")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_USER)
    public BaseResponse<PictureVo> uploadPicture(@RequestPart("file") MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, HttpServletRequest  request){
        //获取登录用户
        User user=userService.vo2obj(userService.getCurrentUser(request));
        PictureVo result=pictureService.uploadPicture(multipartFile,pictureUploadRequest,user);
        return ResultUtils.success(result);
    }

    /**
     * 通过 URL 上传图片（可重新上传）
     */
    @PostMapping("/upload/url")
    public BaseResponse<PictureVo> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.vo2obj(userService.getCurrentUser(request));
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVo pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }


    /**
     * 删除图片
     *本人或者管理员可以删除
     * @param pictureDeleteRequest
     * @param request
     * @return
     */
    @ApiOperation("删除图片")
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_USER)
    public BaseResponse deletePicture(@RequestBody PictureDeleteRequest pictureDeleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureDeleteRequest == null, ErrorCode.PARAMS_ERROR);
        Long deleteId = pictureDeleteRequest.getId();
        if (deleteId == null || deleteId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 查询图片（判断是否存在；判断是否有删除权限）
        Picture oldPicture = pictureService.getById(deleteId);
        ThrowUtils.throwIf(oldPicture==null,ErrorCode.NOT_FOUND_ERROR,"图片不存在");
        //校验权限
        pictureService.checkPictureAuth(userService.vo2obj(userService.getCurrentUser(request)), oldPicture);

        //开启事务：删除图片并更新额度
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = pictureService.removeById(deleteId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"删除失败");
            // 释放额度
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });
        // 异步清理文件
        pictureService.clearPictureFile(oldPicture);
        return ResultUtils.success();
    }

    /**
     * 更新图片
     * 仅管理员可用
     */
    @ApiOperation("更新图片（管理员）")
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_USER)
    public BaseResponse<Long> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        //判断图片是否存在
        Picture oldpicture = pictureService.getById(pictureUpdateRequest.getId());
        ThrowUtils.throwIf(oldpicture == null, ErrorCode.NOT_FOUND_ERROR, "更新图片（管理员）图片不存在");

        //更新数据
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureUpdateRequest, picture);
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));

        //校验更新的合法性
        pictureService.validPicture(picture);

        //补充填充审核状态
        UserVo loginUser = userService.getCurrentUser(request);
        pictureService.fillReviewParam(picture, loginUser);

        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片更新失败");
        return ResultUtils.success();
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @ApiOperation("获取图片")
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_ADMIN)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @ApiOperation("获取图片（封装类）")
    @GetMapping("/get/vo")
    public BaseResponse<PictureVo> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间的图片，需要校验权限
        Space space = null;
        Long spaceId = picture.getSpaceId();
        if (spaceId != null) {
            //TODO 权限校验，私有空间：所有者访问，团队空间：……
            //ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        // 获取权限列表
        User loginUser =userService.vo2obj(userService.getCurrentUser(request));
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        PictureVo pictureVO = pictureService.obj2Vo(picture);
        pictureVO.setPermissionList(permissionList);
        // 获取封装类
        return ResultUtils.success(pictureVO);
    }


    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @ApiOperation("分页获取图片列表")
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_ADMIN)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        log.info("查询PictureList，查询页码为"+current+"页，页大小为"+size);
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装类）
     * 需要过滤未过审数据
     */
    @ApiOperation("获取图片列表（封装类）")
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVo>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        log.info("查询PictureVoList，查询页码为"+current+"页，页大小为"+size);
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        //空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        // 公开图库
        if (spaceId == null) {
            // 普通用户默认只能查看已过审的公开数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
            //普通用户只能看到通过审核的图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        } else {
            // 私有空间
            UserVo loginUser = userService.getCurrentUser(request);
            Space space = spaceService.getById(spaceId);
            log.info("space存在: " + space);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
        }


        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        return ResultUtils.success(pictureService.picturePage2PictureVoPage(picturePage, request));
    }

//    /**
//     * 通过缓存分页获取图片列表（封装类）
//     * Redis
//     * 需要过滤未过审数据
//     */
//    @ApiOperation("获取图片列表(封装类):缓存")
//    @PostMapping("/list/page/vo/cache")
//    public BaseResponse<Page<PictureVo>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
//        //TODO
//        long current = pictureQueryRequest.getCurrent();
//        long size = pictureQueryRequest.getPageSize();
//        // 限制爬虫
//        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
//        //普通用户只能看到通过审核的图片
//        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
//
//        //查询缓存
//        //获得redis key
//        String hashkey= ObjKeyUtils.getKeyByMd5(pictureQueryRequest);
//        String redisKey="belllpicture:listPictureVoByPage:"+hashkey;
//        //查询缓存
//        ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();
//        String storeValue=valueOperations.get(redisKey);
//        //缓存命中，直接返回
//        if(storeValue!=null){
//            Page<PictureVo> cachePage = JSONUtil.toBean(storeValue,Page.class);
//            return ResultUtils.success(cachePage);
//        }
//
//
//        //缓存未命中，查询数据库
//        // 查询数据库
//        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), pictureService.getQueryWrapper(pictureQueryRequest));
//        // 获取封装类
//        Page<PictureVo> result = pictureService.picturePage2PictureVoPage(picturePage, request);
//
//        //存入Redis
//        String cacheValue=JSONUtil.toJsonStr(result);
//        //设置超时(5-10分钟)
//        int cacheExpireTime=300+ RandomUtil.randomInt(0, 300);
//        valueOperations.set(redisKey,cacheValue,cacheExpireTime, TimeUnit.SECONDS);
//
//        //返回结果
//        return ResultUtils.success(result);
//    }

    /**
     * 获取图片列表（封装类）：多级缓存
     * 先在本地缓存查找；未命中，查询Reids缓存；未命中，查询数据库
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    @ApiOperation("获取图片列表（封装类）：多级缓存")
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVo>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                      HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户默认只能查看已过审的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 构建缓存 key
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String redisKey = "yupicture:listPictureVOByPage:" + hashKey;

        //本地缓存中查询
        String cachedValue= LOCAL_CACHE.getIfPresent(hashKey);
        if(cachedValue!=null){
            Page<PictureVo> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 从 Redis 缓存中查询
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        cachedValue = valueOps.get(redisKey);
        if (cachedValue != null) {
            // 如果缓存命中，将数据存到本地缓存
            LOCAL_CACHE.put(hashKey, cachedValue);
            Page<PictureVo> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        Page<PictureVo> pictureVOPage = pictureService.picturePage2PictureVoPage(picturePage, request);

        // 存入 Redis 缓存
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 5 - 10 分钟随机过期，防止雪崩
        int cacheExpireTime = 300 +  RandomUtil.randomInt(0, 300);
        valueOps.set(redisKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);
        //存入本地缓存
        LOCAL_CACHE.put(hashKey, cacheValue);

        // 返回结果
        return ResultUtils.success(pictureVOPage);
    }


    /**
     * 编辑图片（给用户使用）
     */
    @ApiOperation("用户编辑图片")
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        pictureService.validPicture(picture);
        UserVo loginUser = userService.getCurrentUser(request);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        pictureService.checkPictureAuth(userService.vo2obj(loginUser), oldPicture);


        //补充填充审核参数
        pictureService.fillReviewParam(picture, loginUser);

        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 获取标签分类
     *
     * @return
     */
    @ApiOperation("获取标签、分类")
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategoryVo> listPictureTagCategory() {
        PictureTagCategoryVo pictureTagCategory = new PictureTagCategoryVo();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }


    /**
     * 审核图片请求
     */
    @ApiOperation("审核图片")
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_ADMIN)
    public BaseResponse reviewPicture(@RequestBody PictureReviewRequest pictureReviewRequest,
                                      HttpServletRequest request) {
        //校验请求参数
        ThrowUtils.throwIf(pictureReviewRequest==null, ErrorCode.PARAMS_ERROR,"请求参数为空");
        //获取当前登录用户
        UserVo user=userService.getCurrentUser(request);
        //执行
        pictureService.doReviewPicture(pictureReviewRequest,user);
        return ResultUtils.success();
    }

    /**
     * 批量上传图片
     * @param pictureUploadByBatchRequest
     * @param request
     * @return
     */
    @ApiOperation("批量上传图片")
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.USER_ROLE_ADMIN)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.vo2obj(userService.getCurrentUser(request));
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }

    /**
     * 获取空间等级
     * @return
     */
    @ApiOperation("获取空间等级")
    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values()) // 获取所有枚举
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }

    /**
     * 以图搜图
     */
    @ApiOperation("以图搜图")
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        List<ImageSearchResult> resultList = ImageSearchApiFacade.searchImage(oldPicture.getUrl());
        return ResultUtils.success(resultList);
    }


    /**
     * 颜色搜图
     * @param searchPictureByColorRequest
     * @param request
     * @return
     */
    @ApiOperation("颜色搜图")
    @PostMapping("/search/color")
    public BaseResponse<List<PictureVo>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        User loginUser = userService.vo2obj(userService.getCurrentUser(request));
        List<PictureVo> result = pictureService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(result);
    }


    /**
     * 批量编辑图片
     * @param pictureEditByBatchRequest
     * @param request
     * @return
     */
    @ApiOperation("批量编辑图片")
    @PostMapping("/edit/batch")
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.vo2obj(userService.getCurrentUser(request));
        pictureService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }


    /**
     * 创建 AI 扩图任务
     */
    @ApiOperation("创建 AI 扩图任务")
    @PostMapping("/out_painting/create_task")
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(
            @RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
            HttpServletRequest request) {
        log.info("接口调用：创建 AI 扩图任务 ");
        if (createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.vo2obj(userService.getCurrentUser(request));
        CreateOutPaintingTaskResponse response = pictureService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(response);
    }

    /**
     * 查询 AI 扩图任务
     */
    @ApiOperation("查询 AI 扩图任务")
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        log.info("接口调用：查询 AI 扩图任务 ");
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);
        GetOutPaintingTaskResponse task = aliYunAiApi.getOutPaintingTask(taskId);
        return ResultUtils.success(task);
    }



}
