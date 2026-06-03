/*
 *
 *                Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
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

import com.dialoguebranch.web.service.auth.AuthenticationInfo;
import com.dialoguebranch.web.service.Configuration;
import com.dialoguebranch.web.service.auth.basic.BasicUserCredentials;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

/**
 * Collection of static methods for creating and verifying JSON Web Tokens (JWTs).
 *
 * @author Harm op den Akker
 * @author Dennis Hofs
 */
public class JWTUtils {

    /** Used to access configuration parameters */
    private static final Configuration config = Configuration.getInstance();

    /**
     * Generates an access token (JWT) based on the given {@link BasicUserCredentials}.
     *
     * @param basicUserCredentials object containing username, and roles of the user for which to
     *                             generate a JWT.
     * @return the JWT access token as a String.
     */
    public static String generateAccessToken(BasicUserCredentials basicUserCredentials) {
        return Jwts.builder()
                .expiration(new Date(System.currentTimeMillis() + config.getAccessTokenExpirationSeconds() * 1000L))
                .issuedAt(new Date())
                .issuer(config.getBaseUrl())
                .subject(basicUserCredentials.getUsername())
                .claim("typ","Bearer") // Type of Token
                .claim("azp","dlb-web-service") // Authorized party
                .claim("roles", basicUserCredentials.getCommaSeparatedRolesString())
                .signWith(getAccessTokenSecret())
                .compact();
    }

    /**
     * Generates a refresh token (JWT) based on the given {@link BasicUserCredentials}.
     *
     * @param basicUserCredentials object containing username, and roles of the user for which to
     *                             generate a JWT.
     * @return the JWT refresh token as a String.
     */
    public static String generateRefreshToken(BasicUserCredentials basicUserCredentials) {
        return Jwts.builder()
                .expiration(new Date(System.currentTimeMillis() + config.getRefreshTokenExpirationSeconds() * 1000L))
                .issuedAt(new Date())
                .issuer(config.getBaseUrl())
                .audience().add(config.getBaseUrl()).and()
                .subject(basicUserCredentials.getUsername())
                .claim("typ","Refresh") // Type of Token
                .claim("azp","dlb-web-service") // Authorized party
                .signWith(getRefreshTokenSecret())
                .compact();
    }

    public static <T> T extractClaims(String token, Function<Claims, T> claimFunction) {
        Claims claims = Jwts.parser()
                .verifyWith(getAccessTokenSecret())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimFunction.apply(claims);
    }

    public static AuthenticationInfo isAccessTokenValid(String token)
            throws JwtException {
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

    public static AuthenticationInfo isRefreshTokenValid(String refreshToken)
            throws JwtException {
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

    /**
     * Obtains a {@link SecretKey} object used for encrypting and decrypting Access Tokens by
     * parsing the Base64 string value as defined in the configuration property
     * jwtAccessTokenSecret.
     *
     * @return the secret key as a {@link SecretKey} object.
     */
    private static SecretKey getAccessTokenSecret() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(Configuration.getInstance()
                .getJwtAccessTokenSecret()));
    }

    /**
     * Obtains a {@link SecretKey} object used for encrypting and decrypting Access Tokens by
     * parsing the Base64 string value as defined in the configuration property
     * jwtAccessTokenSecret.
     *
     * @return the secret key as a {@link SecretKey} object.
     */
    private static SecretKey getRefreshTokenSecret() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(Configuration.getInstance()
                .getJwtRefreshTokenSecret()));
    }

}
