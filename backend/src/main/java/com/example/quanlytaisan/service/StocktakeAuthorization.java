package com.example.quanlytaisan.service;

import com.example.quanlytaisan.repository.StocktakeSessionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("stocktakeAuthorization")
public class StocktakeAuthorization {
    private final StocktakeSessionRepository sessionRepository;

    public StocktakeAuthorization(StocktakeSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public boolean canAccess(Integer sessionId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        if (authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) return true;
        return sessionRepository.findById(sessionId)
                .map(session -> session.getResponsibleUsername() != null
                        && session.getResponsibleUsername().equalsIgnoreCase(authentication.getName()))
                .orElse(false);
    }
}
