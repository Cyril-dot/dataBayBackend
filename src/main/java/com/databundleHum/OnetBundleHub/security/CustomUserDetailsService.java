package com.databundleHum.OnetBundleHub.security;

import com.databundleHum.OnetBundleHub.entity.User;
import com.databundleHum.OnetBundleHub.repos.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));

        if (!user.isActive()) {
            throw new DisabledException("Account is deactivated. Please contact support.");
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                user.isActive(),
                true, // accountNonExpired
                true, // credentialsNonExpired
                true, // accountNonLocked
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
        );
    }
}