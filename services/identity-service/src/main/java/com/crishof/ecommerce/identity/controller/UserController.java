package com.crishof.ecommerce.identity.controller;

import com.crishof.ecommerce.identity.repository.UserRepository;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> findById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(u -> ResponseEntity.ok(Map.<String, Object>of(
                        "id", u.getId(),
                        "email", u.getEmail(),
                        "name", u.getName(),
                        "active", u.getActive()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/exists")
    public ResponseEntity<Map<String, Boolean>> exists(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("exists", userRepository.existsById(id)));
    }
}
