package com.belllsheep.bellpicture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.belllsheep.bellpicture.api.aliyunai.AliYunAiApi;
import com.belllsheep.bellpicture.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.belllsheep.bellpicture.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.belllsheep.bellpicture.constant.UserConstant;
import com.belllsheep.bellpicture.exception.BusinessException;
import com.belllsheep.bellpicture.exception.ErrorCode;
import com.belllsheep.bellpicture.exception.ThrowUtils;
import com.belllsheep.bellpicture.manager.CosManager;
import com.belllsheep.bellpicture.manager.upload.FilePictureUpload;
import com.belllsheep.bellpicture.manager.upload.PictureUploadTemplate;
import com.belllsheep.bellpicture.manager.upload.UrlPictureUpload;
import com.belllsheep.bellpicture.model.dto.picture.*;
import com.belllsheep.bellpicture.model.dto.file.UploadPictureResult;
import com.belllsheep.bellpicture.model.entity.Picture;
import com.belllsheep.bellpicture.model.entity.Space;
import com.belllsheep.bellpicture.model.entity.User;
import com.belllsheep.bellpicture.model.enums.PictureReviewStatusEnum;
import com.belllsheep.bellpicture.model.enums.UserRoleEnum;
import com.belllsheep.bellpicture.model.vo.PictureVo;
import com.belllsheep.bellpicture.model.vo.UserVo;
import com.belllsheep.bellpicture.service.PictureService;
import com.belllsheep.bellpicture.mapper.PictureMapper;
import com.belllsheep.bellpicture.service.SpaceService;
import com.belllsheep.bellpicture.service.UserService;
import com.belllsheep.bellpicture.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author 何欣
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-07-24 15:10:24
*/

@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

    @Resource
    private UserService userService;
    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private CosManager cosManager;

    @Resource
    private SpaceService spaceService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

    /**
     * 上传图片（新增图片/更改已有图片）
     * 开放用户上传之后要对图片进行审核
     * @param inputSource
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    @Override
    public PictureVo uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        PictureUploadTemplate uploadTemplate = null;
        if(inputSource instanceof MultipartFile){
            uploadTemplate = filePictureUpload;
        }else if(inputSource instanceof String){
            uploadTemplate = urlPictureUpload;
        }

        ThrowUtils.throwIf(inputSource == null, ErrorCode.PARAMS_ERROR,"文件不能为空");

        //空间相关校验
        //获取空间id,仅有空间主人有权限上传图片
        Long spaceId = pictureUploadRequest.getSpaceId();
        if(spaceId!=null){
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space==null,ErrorCode.NOT_FOUND_ERROR,"空间不存在");
            ThrowUtils.throwIf(
                    !(space.getUserId().equals(loginUser.getId())),
                    ErrorCode.NO_AUTH_ERROR,
                    "没有权限上传图片");
            //校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }

            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }
        //判断图片是更新还是新增
        Long pictureId=null;
        if(pictureUploadRequest!=null && pictureUploadRequest.getId()!=null){
            pictureId=pictureUploadRequest.getId();
        }

        //如果是更新图片，判断图片是否存在
        if(pictureId!=null){
            Picture  picture = this.getById(pictureId);
            ThrowUtils.throwIf(picture == null,ErrorCode.NOT_FOUND_ERROR,"图片不存在");
            //判断用户是否有修改权限,没有则抛出异常
            ThrowUtils.throwIf(
                    !(picture.getUserId().equals(loginUser.getId()) || loginUser.getUserRole().equals(UserRoleEnum.ADMIN.getValue())),
                    ErrorCode.NO_AUTH_ERROR,
                    "没有权限修改该图片");
            this.clearPictureFile( picture);
            //校验空间是否一致
            if(spaceId==null && picture.getSpaceId()!=null){
                spaceId=picture.getSpaceId();
            }else{
                ThrowUtils.throwIf(!picture.getSpaceId().equals(spaceId),ErrorCode.PARAMS_ERROR,"空间不一致");
            }


        }

        //上传图片到云，获得图片信息
        // 按照用户 id 划分目录 => 按照空间划分目录
        String pathPrefix;
        if (spaceId == null) {
            pathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            pathPrefix = String.format("space/%s", spaceId);
        }

        UploadPictureResult uploadPicture= uploadTemplate.uploadPicture(inputSource,pathPrefix);

        //构建图片信息
        Picture picture=new Picture();

        picture.setUrl(uploadPicture.getUrl());
        String picName = uploadPicture.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setSpaceId(spaceId);
        picture.setName(picName);
        picture.setUrl(uploadPicture.getUrl());
        picture.setPicSize(uploadPicture.getPicSize());
        picture.setPicWidth(uploadPicture.getPicWidth());
        picture.setPicHeight(uploadPicture.getPicHeight());
        picture.setPicScale(uploadPicture.getPicScale());
        picture.setPicFormat(uploadPicture.getPicFormat());
        picture.setUserId(loginUser.getId());
        picture.setPicColor(uploadPicture.getPicColor());



        if(pictureId!=null){
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        //补充设置审核状态
        fillReviewParam( picture, loginUser);
        //补充设置略缩图字段
        picture.setThumbnailUrl(uploadPicture.getThumbnailUrl());

        //保存到数据库并更新额度
        //开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
            if (finalSpaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture;
        });

        //返回图片信息Vo
        return PictureVo.obj2Vo(picture);
    }

    /**
     * 获取查询器
     *
     * @param pictureQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if(pictureQueryRequest!=null){
            Long id=pictureQueryRequest.getId();
            String name=pictureQueryRequest.getName();
            String introduction=pictureQueryRequest.getIntroduction();
            String category=pictureQueryRequest.getCategory();
            List<String> tags=pictureQueryRequest.getTags();
            Long picSize=pictureQueryRequest.getPicSize();
            Integer picWidth=pictureQueryRequest.getPicWidth();
            Integer picHeight=pictureQueryRequest.getPicHeight();
            Double picScale=pictureQueryRequest.getPicScale();
            String picFormat=pictureQueryRequest.getPicFormat();
            String searchText=pictureQueryRequest.getSearchText();
            Long userId=pictureQueryRequest.getUserId();
            String sortField=pictureQueryRequest.getSortField();
            String sortOrder=pictureQueryRequest.getSortOrder();
            Integer reviewStatus=pictureQueryRequest.getReviewStatus();
            String reviewMessage=pictureQueryRequest.getReviewMessage();
            Long reviewerId=pictureQueryRequest.getReviewerId();
            Long spaceId=pictureQueryRequest.getSpaceId();
            boolean nullSpaceId=pictureQueryRequest.isNullSpaceId();
            Date startEditTime = pictureQueryRequest.getStartEditTime();
            Date endEditTime = pictureQueryRequest.getEndEditTime();


            queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
            queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
            queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
            queryWrapper.isNull(nullSpaceId, "spaceId");
            queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
            queryWrapper.like(StrUtil.isNotBlank( name), "name", name);
            queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
            queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
            if(CollUtil.isNotEmpty( tags)){
                for(String tag: tags){
                    queryWrapper.like(StrUtil.isNotBlank(tag), "tags",
                            String.format("\"%s\"",tag));
                }
            }
            queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
            queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
            queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
            queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
            queryWrapper.eq(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
            if(StrUtil.isNotBlank(searchText)){
                queryWrapper.and(qw-> qw
                        .like("name", searchText))
                        .or()
                        .like("introduction", searchText);
            }
            queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
            queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
            queryWrapper.like(ObjUtil.isNotEmpty(reviewMessage), "reviewMessage", reviewMessage);
            queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);

            //排序
            queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);

        }
        return queryWrapper;
    }

    /**
     * Picture对象转Vo
     * 填充创建用户vo
     * @param picture
     * @return
     */
    @Override
    public PictureVo obj2Vo(Picture picture) {
        PictureVo pictureVo = PictureVo.obj2Vo( picture);
        //设置创建用户vo
        if(picture.getUserId()!=null){
            //从数据库中查询user
            User user = userService.getById(picture.getUserId());
            UserVo userVo = userService.obj2UserVo( user);
            pictureVo.setUser(userVo);
        }
        return pictureVo;
    }

    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVo> picturePage2PictureVoPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVo> pictureVoPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVoPage;
        }

        // 对象列表 => 封装对象列表
        List<PictureVo> pictureVOList = pictureList.stream().map(PictureVo::obj2Vo).collect(Collectors.toList());
        //搜索所有相关用户，将其填充到对应的PictureVo中
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.obj2UserVo(user));
        });
        pictureVoPage.setRecords(pictureVOList);
        return pictureVoPage;
    }

    /**
     * 图片校验
     * 错误直接抛出异常
     * 更新和修改图片时使用
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture==null, ErrorCode.PARAMS_ERROR,"图片为空");
        //TODO 自定义图片更新/修改规则
        ThrowUtils.throwIf(picture.getId()==null, ErrorCode.PARAMS_ERROR,"图片id不能为空");
        ThrowUtils.throwIf(picture.getUrl()!=null && picture.getUrl().length()>1024, ErrorCode.PARAMS_ERROR,"图片url过长");
        ThrowUtils.throwIf(picture.getIntroduction()!=null && picture.getIntroduction().length()>1024, ErrorCode.PARAMS_ERROR,"图片简介过长");
    }

    /**
     * 审核图片请求
     * 如果审核有异常直接抛出错误信息
     * @param pictureReviewRequest
     * @param loginUser 审核员
     */
    @Override
    public void doReviewPicture(PictureReviewRequest pictureReviewRequest, UserVo loginUser) {
        //1、校验参数（数据是否存在）
        ThrowUtils.throwIf(pictureReviewRequest==null, ErrorCode.PARAMS_ERROR,"请求参数为空");

        //判断审核状态参数是否正确
        Integer status = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum pictureReviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(status);
        Long id=pictureReviewRequest.getId();
        String message=pictureReviewRequest.getReviewMessage();
        ThrowUtils.throwIf(id == null || message == null || pictureReviewStatusEnum==null, ErrorCode.PARAMS_ERROR,"审核参数错误");

        //获取图片，判断图片是否存在
        Picture picture = this.getById(pictureReviewRequest.getId());
        ThrowUtils.throwIf(picture==null, ErrorCode.NOT_FOUND_ERROR,"图片不存在");

        //判断审核状态是否重复
        ThrowUtils.throwIf(picture.getReviewStatus().equals(status),ErrorCode.OPERATION_ERROR,"请勿重复审核");

        //2、更改图片信息
        picture.setReviewerId(id);
        picture.setReviewTime(new Date());
        picture.setReviewStatus(pictureReviewRequest.getReviewStatus());
        picture.setReviewMessage(pictureReviewRequest.getReviewMessage());
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"审核失败");
    }

    /**
     * 填充审核参数
     */
    @Override
    public void fillReviewParam(Picture picture, UserVo loginUser){
        //如果当前用户是管理员，自动过审
        if(loginUser.getUserRole().equals(UserConstant.USER_ROLE_ADMIN)) {
            picture.setReviewerId(loginUser.getId());
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewTime(new Date());
            picture.setReviewMessage("管理员自动过审");
        }else{
            //非管理员，审核状态设置为：待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    @Override
    public void fillReviewParam(Picture picture, User loginUser){
        UserVo userVo=new UserVo();
        BeanUtil.copyProperties(loginUser, userVo);
        fillReviewParam(picture, userVo);
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        //参数校验；异常处理
        ThrowUtils.throwIf(pictureUploadByBatchRequest==null,ErrorCode.PARAMS_ERROR,"参数为空");
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count>30,ErrorCode.PARAMS_ERROR,"最多抓取30张图片");
        ThrowUtils.throwIf(StrUtil.isBlank(searchText),ErrorCode.PARAMS_ERROR,"搜索内容不能为空");

        String namePrefix= pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)){
            namePrefix=searchText;
        }
        //抓取内容
        //拼接请求
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try{
            document= (Document) Jsoup.connect(fetchUrl).get();
        }catch (IOException e){
            log.error("图片抓取失败",e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"图片抓取失败");
        }

        //解析内容
        Elements elements = document.select("img.mimg");
        int uploadCount = 0;
        for(Element element:elements){
            String fileUrl = element.attr("src");
            if(StrUtil.hasBlank(fileUrl)){
                log.info("当前连接为空，已跳过:{}", fileUrl);
                continue;
            }
            //处理图片上传地址，防止出现转移问题
            int index = fileUrl.indexOf("?");
            if(index>-1){
                fileUrl = fileUrl.substring(0, index);
            }

            //上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            if(StrUtil.isNotBlank(namePrefix)){
                pictureUploadRequest.setPicName(namePrefix+(uploadCount+1));
            }
            try{
                PictureVo pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功,id={}",pictureVO.getId());
                uploadCount++;
            }catch (Exception e){
                log.error("图片上传失败",e);
                continue;
            }
            if(uploadCount>=count){
                break;
            }
        }

        //上传图片
        return uploadCount;
    }

    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断该图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }
        // FIXME 注意，这里的 url 包含了域名，实际上只要传 key 值（存储路径）就够了
        cosManager.deleteObject(oldPicture.getUrl());
        // 清理缩略图
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            cosManager.deleteObject(thumbnailUrl);
        }
    }


    /**
     * 检查图片权限
     *
     * @param loginUser
     * @param picture
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    /**
     * 根据颜色搜索图片
     * @param spaceId
     * @param picColor
     * @param loginUser
     * @return
     */
    @Override
    public List<PictureVo> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询该空间下所有图片（必须有主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }
        // 将目标颜色转为 Color 对象
        Color targetColor = Color.decode(picColor);
        // 4. 计算相似度并排序
        List<Picture> sortedPictures = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    // 提取图片主色调
                    String hexColor = picture.getPicColor();
                    // 没有主色调的图片放到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 越大越相似
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                // 取前 12 个
                .limit(12)
                .collect(Collectors.toList());

        // 转换为 PictureVO
        return sortedPictures.stream()
                .map(PictureVo::obj2Vo)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        

        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }

        // 3. 查询指定图片，仅选择需要的字段
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();

        if (pictureList.isEmpty()) {
            return;
        }
        

        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        
        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);

        // 5. 批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }


    /**
     * 创建扩图任务
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     * @return
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        // 权限校验
        checkPictureAuth(loginUser, picture);
        // 构造请求参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        taskRequest.setInput(input);
        BeanUtil.copyProperties(createPictureOutPaintingTaskRequest, taskRequest);
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(taskRequest);
    }



}




