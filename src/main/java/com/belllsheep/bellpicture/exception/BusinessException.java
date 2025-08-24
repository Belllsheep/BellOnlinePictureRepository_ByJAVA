package com.belllsheep.bellpicture.exception;

import lombok.Data;
import lombok.Getter;

//@Data
//public class BusinessException extends RuntimeException{
//    private final int code;
//
//    BusinessException(int code, String errorMsg){
//        super(errorMsg);
//        this.code = code;
//    }
//
//    BusinessException(ErrorCode errorCode){
//        super(errorCode.getMessage());
//        this.code = errorCode.getCode();
//    }
//
//    BusinessException(ErrorCode errorCode, String message){
//        super(message);
//        this.code = errorCode.getCode();
//    }
//}
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

}
