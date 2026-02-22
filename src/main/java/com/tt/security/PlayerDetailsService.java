package com.tt.security;

import com.tt.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlayerDetailsService implements UserDetailsService {

    private final PlayerRepository playerRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var player = playerRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Not found: " + email));
        return build(player);
    }

    public UserDetails loadUserById(Long id) {
        var player = playerRepository.findById(id)
            .orElseThrow(() -> new UsernameNotFoundException("Not found: " + id));
        return build(player);
    }

    private UserDetails build(com.tt.model.Player p) {
        return new org.springframework.security.core.userdetails.User(
            String.valueOf(p.getId()),
            p.getPasswordHash(),
            List.of(new SimpleGrantedAuthority("ROLE_PLAYER"))
        );
    }
}
