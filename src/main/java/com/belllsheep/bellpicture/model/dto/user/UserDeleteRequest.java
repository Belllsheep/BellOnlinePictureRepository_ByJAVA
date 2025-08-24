package com.belllsheep.bellpicture.model.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class UserDeleteRequest implements Serializable {
    private Long id;
}
