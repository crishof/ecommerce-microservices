package com.crishof.ecommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.crishof.ecommerce.identity.controller.UserController;
import com.crishof.ecommerce.identity.domain.User;
import com.crishof.ecommerce.identity.repository.UserRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserController controller;

    @Test
    void returnsUserWhenFound() {
        User user = new User();
        user.setId(2L);
        user.setEmail("alice@shop.com");
        user.setName("Alice Buyer");
        user.setActive(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        ResponseEntity<Map<String, Object>> response = controller.findById(2L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("email", "alice@shop.com");
    }

    @Test
    void returns404WhenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.findById(99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
