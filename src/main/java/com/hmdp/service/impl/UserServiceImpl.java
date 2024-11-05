package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if (phoneInvalid){
            //如果不符合返回错误信息
            return Result.fail("手机号格式错误");
        }
//        符合，生成验证码
        //随机生成6位
        String code = RandomUtil.randomNumbers(6);
//        保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //生成有效期

//                保持验证码到session
//        session.setAttribute("code",code);
        //发送验证码
        log.debug("发送短信验证码成功，验证码：{}",code);
//                返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone =loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            return  Result.fail("手机号格式错误");
        }
        //校验验证码  从redis中获取
        String cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
//        Object cachecode = session.getAttribute("code");
        String code=loginForm.getCode();
        if (cachecode==null||!cachecode.equals(code)){
            return Result.fail("验证码错误");
        }
//        不一致报错
//                一致根据手机号查询用户
        User user=query().eq("phone",phone).one();
//        判断用户是否存在
//                不存在
        if(user==null){
             user=createUserWithPhone(phone);
        }
//        存在 保存用户信息到redis
        //随机生成token
        String token = UUID.randomUUID().toString(true);
        //uer对象转换为哈希
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        /*
        为什么要折磨做，因为仅仅的map(string,object>是没法存进redis的，因为用了stringtemplate，这个是要求所有的都是string
        而可以在beantomap中细致指定转换string方法
         */
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fileValue)->fileValue.toString()));

//        存储
        String tokenKey=LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,stringObjectMap);
        //设置有效期 每次访问后还要更新，一致不访问才会过期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);

//                返回token
//        session.setAttribute("user",user);//session 方法
        return  Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        //保存用户
        save(user);
        return user;
    }
}
