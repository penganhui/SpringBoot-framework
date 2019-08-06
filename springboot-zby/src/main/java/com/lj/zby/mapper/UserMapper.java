package com.lj.zby.mapper;

import com.lj.zby.entity.User;
import org.springframework.stereotype.Repository;

@Repository
public interface UserMapper {
    User findUserById(String userId);
    User findByUsername(User userName);
}
