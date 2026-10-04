package com.seatbook.api;

import com.seatbook.config.AppProperties;
import com.seatbook.error.ApiException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;

/** Convenience for the burst script / reviewers. user tokens are open; ADMIN needs X-Admin-Secret. Disable via DEV_TOKENS_ENABLED=false. */
@RestController
public class DevTokenController {
    private final JwtEncoder encoder;
    private final AppProperties props;

    public DevTokenController(JwtEncoder encoder, AppProperties props) { this.encoder = encoder; this.props = props; }

    @PostMapping("/auth/dev-token")
    Map<String, String> mint(@RequestParam String user, @RequestParam(defaultValue = "USER") String role,
                             @RequestHeader(value = "X-Admin-Secret", required = false) String adminSecret) {
        if (!props.devTokensEnabled()) throw new ApiException(HttpStatus.NOT_FOUND, "disabled");
        if (user.isBlank() || user.length() > 128) throw ApiException.badRequest("bad user");
        String r = role.toUpperCase();
        if (r.equals("ADMIN")) {
            if (adminSecret == null || !MessageDigest.isEqual(adminSecret.getBytes(StandardCharsets.UTF_8),
                    props.adminBootstrapSecret().getBytes(StandardCharsets.UTF_8)))
                throw ApiException.forbidden("admin secret required");
        } else r = "USER";
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().subject(user).claim("role", r).issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(24))).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return Map.of("token", token, "user", user, "role", r);
    }
}
