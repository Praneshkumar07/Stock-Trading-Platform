package com.trading.userservice.repository;

import com.trading.userservice.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,String> {

    public boolean existsByEmail(String email);
    public Optional<User> findByEmail(String email);

}
