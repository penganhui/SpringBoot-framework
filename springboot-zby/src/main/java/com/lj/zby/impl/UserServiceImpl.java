package com.lj.zby.impl;

import com.lj.zby.entity.User;
import com.lj.zby.mapper.UserMapper;
import com.lj.zby.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    UserMapper userMapper;
    @Override
    public User findUserById(String user) {
        return userMapper.findUserById(user);
    }

    @Override
    public User findByUsername(User user) {
        return userMapper.findByUsername(user);
    }
}
