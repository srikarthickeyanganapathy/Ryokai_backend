package com.example.taskflow.service;

import java.util.stream.Collectors;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.taskflow.domain.User;
import com.example.taskflow.repository.UserRepository;

import java.util.Collection;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public static class CustomUserDetails extends org.springframework.security.core.userdetails.User {
        private final Integer tokenVersion;

        public CustomUserDetails(User user, Collection<? extends org.springframework.security.core.GrantedAuthority> authorities) {
            super(user.getUsername(), user.getPassword(), authorities);
            this.tokenVersion = user.getTokenVersion();
        }

        public Integer getTokenVersion() {
            return tokenVersion;
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        User user = userRepository.findByUsername(username).orElseThrow(()->new UsernameNotFoundException("User not Found "+ username));
        return new CustomUserDetails(
                user,
                user.getRoles().stream()
                .map(role -> {
                    String roleName = role.getName();
                    if (!roleName.startsWith("ROLE_")) {
                        roleName = "ROLE_" + roleName;
                    }
                    return new SimpleGrantedAuthority(roleName);
                })
                .collect(Collectors.toList()));
    }
}