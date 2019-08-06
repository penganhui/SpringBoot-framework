package com.lj.zby.service;

import com.lj.zby.entity.User;

public interface UserService {
    User findUserById(String user);
    User findByUsername(User user);
}
