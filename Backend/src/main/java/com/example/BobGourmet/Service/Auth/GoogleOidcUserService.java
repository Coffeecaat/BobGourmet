package com.example.BobGourmet.Service.Auth;

import com.example.BobGourmet.DTO.AuthDTO.GoogleUserInfo;
import com.example.BobGourmet.Entity.User;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class GoogleOidcUserService extends OidcUserService {
    private final OAuth2UserService users;

    public GoogleOidcUserService(OAuth2UserService users) {
        this.users = users;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        if (!"google".equals(request.getClientRegistration().getRegistrationId())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("unsupported_provider"));
        }
        // Spring validates the ID token before this call and the UserInfo subject in super.loadUser.
        OidcUser oidcUser = super.loadUser(request);
        GoogleUserInfo info = GoogleUserInfo.builder()
                .sub(oidcUser.getSubject()).email(oidcUser.getEmail())
                .emailVerified(oidcUser.getEmailVerified())
                .name(oidcUser.getFullName()).givenName(oidcUser.getGivenName())
                .familyName(oidcUser.getFamilyName()).picture(oidcUser.getClaimAsString("picture")).build();
        try {
            User user = users.findOrCreateUser(info);
            return new LocalOidcUser(oidcUser, user.getUsername(), user.getNickname());
        } catch (RuntimeException exception) {
            throw new OAuth2AuthenticationException(new OAuth2Error("user_creation_failed"), exception);
        }
    }

    public static final class LocalOidcUser extends DefaultOidcUser {
        private final String username;
        private final String nickname;

        public LocalOidcUser(OidcUser user, String username, String nickname) {
            super(user.getAuthorities(), user.getIdToken(), user.getUserInfo());
            this.username = username;
            this.nickname = nickname;
        }

        public String getLocalUsername() {
            return username;
        }

        public String getLocalNickname() {
            return nickname;
        }
    }
}
