/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.dialoguebranch.web.service.auth.jwt;

import com.dialoguebranch.web.service.DlbProperties;
import com.dialoguebranch.web.service.auth.AuthenticationInfo;
import com.dialoguebranch.web.service.auth.basic.BasicUserCredentials;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

/**
 * Spring-managed bean providing JWT creation and validation for the native authentication service.
 *
 * @author Harm op den Akker
 * @author Dennis Hofs
 */
@Component
public class JWTUtils {

    private final DlbProperties dlbProperties;

    @Autowired
    public JWTUtils(DlbProperties dlbProperties) {
        this.dlbProperties = dlbProperties;
    }

    public String generateAccessToken(BasicUserCredentials basicUserCredentials) {
        DlbProperties.Auth auth = dlbProperties.getAuth();
        return Jwts.builder()
                .expiration(new Date(System.currentTimeMillis()
                        + auth.getAccessTokenExpirationSeconds() * 1000L))
                .issuedAt(new Date())
                .issuer(dlbProperties.getBaseUrl())
                .subject(basicUserCredentials.getUsername())
                .claim("typ", "Bearer")
                .claim("azp", "dlb-web-service")
                .claim("roles", basicUserCredentials.getCommaSeparatedRolesString())
                .signWith(getAccessTokenSecret())
                .compact();
    }

    public String generateRefreshToken(BasicUserCredentials basicUserCredentials) {
        DlbProperties.Auth auth = dlbProperties.getAuth();
        return Jwts.builder()
                .expiration(new Date(System.currentTimeMillis()
                        + auth.getRefreshTokenExpirationSeconds() * 1000L))
                .issuedAt(new Date())
                .issuer(dlbProperties.getBaseUrl())
                .audience().add(dlbProperties.getBaseUrl()).and()
                .subject(basicUserCredentials.getUsername())
                .claim("typ", "Refresh")
                .claim("azp", "dlb-web-service")
                .signWith(getRefreshTokenSecret())
                .compact();
    }

    public <T> T extractClaims(String token, Function<Claims, T> claimFunction) {
        Claims claims = Jwts.parser()
                .verifyWith(getAccessTokenSecret())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimFunction.apply(claims);
    }

    public AuthenticationInfo isAccessTokenValid(String token) throws JwtException {
        final Claims claims = Jwts.parser()
                .verifyWith(getAccessTokenSecret())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String rolesString = (String) claims.get("roles");
        String[] roles = rolesString.split(",");
        return new AuthenticationInfo(
                claims.getSubject(),
                roles,
                claims.getIssuedAt(),
                claims.getExpiration());
    }

    public AuthenticationInfo isRefreshTokenValid(String refreshToken) throws JwtException {
        final Claims claims = Jwts.parser()
                .verifyWith(getRefreshTokenSecret())
                .build()
                .parseSignedClaims(refreshToken)
                .getPayload();
        return new AuthenticationInfo(
                claims.getSubject(),
                null,
                claims.getIssuedAt(),
                claims.getExpiration());
    }

    private SecretKey getAccessTokenSecret() {
        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(dlbProperties.getAuth().getJwtAccessTokenSecret()));
    }

    private SecretKey getRefreshTokenSecret() {
        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(dlbProperties.getAuth().getJwtRefreshTokenSecret()));
    }
}
