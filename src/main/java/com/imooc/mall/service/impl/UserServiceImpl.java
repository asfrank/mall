package com.imooc.mall.service.impl;

import com.imooc.mall.dao.UserMapper;
import com.imooc.mall.enums.ResponseEnum;
import com.imooc.mall.enums.RoleEnum;
import com.imooc.mall.pojo.User;
import com.imooc.mall.service.IUserService;
import com.imooc.mall.vo.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

@Service
public class UserServiceImpl implements IUserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 注册
     * @param user
     */
    @Override
    public ResponseVo<User> register(User user) {
        //username不能重复
        int countByUsername = userMapper.countByUsername(user.getUsername());
        if (countByUsername > 0) {
//            throw new RuntimeException("该用户名已注册！");
            return ResponseVo.error(ResponseEnum.USERNAME_EXIST);
        }

        //email不能重复
        int countByEmail = userMapper.countByEmail(user.getEmail());
        if (countByEmail > 0) {
//            throw new RuntimeException("该邮箱已注册！");
            return ResponseVo.error(ResponseEnum.EMAIL_EXIST);
        }

        user.setRole(RoleEnum.CUSTOMER.getCode());

        //MD5摘要（spring自带）
        String passWd = DigestUtils.md5DigestAsHex(user.getPassword().getBytes(StandardCharsets.UTF_8));
        user.setPassword(passWd);

        //写入数据库
        int resultCount = userMapper.insertSelective(user);
        if (resultCount == 0) {
//            throw new RuntimeException("注册失败");
            return ResponseVo.error(ResponseEnum.ERROR);
        }

        return ResponseVo.success();
    }

    @Override
    public ResponseVo<User> login(String username, String password) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            //用户不存在
            return ResponseVo.error(ResponseEnum.USERNAME_OR_PASSWORD_ERROR);
        }
        if (!user.getPassword().equalsIgnoreCase(DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8)))) {
            //密码错误
            return ResponseVo.error(ResponseEnum.USERNAME_OR_PASSWORD_ERROR);
        }

        user.setPassword("");
        return ResponseVo.success(user);
    }
}
