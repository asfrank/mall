package com.imooc.mall.enums;

import lombok.Getter;

//1在售，2下架，3删除
@Getter
public enum ProductStatusEnum {
    ON_SALE(1),

    OFF_SALE(2),

    DELETE(3),

    ;
    Integer code;

    ProductStatusEnum(Integer code) {
        this.code = code;
    }
}
