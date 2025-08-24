package com.belllsheep.bellpicture.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PictureTagCategoryVo implements Serializable {
    private List<String> TagList;
    private List<String> CategoryList;
}
