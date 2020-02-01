package com.imooc.mall.form;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class UserRegisterForm {

    @NotBlank //用于String 判断空格
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String email;
}
