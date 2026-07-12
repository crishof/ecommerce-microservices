package com.crishof.ecommerce.identity.repository;

import com.crishof.ecommerce.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
