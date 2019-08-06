package com.lj.zby.service;

import com.lj.zby.entity.User;

public interface TokenService {
    String getToken(User user);
}
