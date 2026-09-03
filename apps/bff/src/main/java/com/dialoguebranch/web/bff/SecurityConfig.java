/*
 *
 *                 Copyright (c) 2023-2026 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
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

package com.dialoguebranch.web.bff;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Session-cookie auth for Dialogue Branch Studio: this service performs the Authorization
 * Code + PKCE exchange against Keycloak and keeps the resulting access/refresh token
 * server-side, in the HTTP session (persisted to MariaDB via Spring Session, not this JVM's own
 * heap — see {@code application.yml}'s {@code spring.session} block). The browser only ever holds
 * the {@code JSESSIONID} cookie, never a raw token.
 *
 * @author Dennis Hofs
 */
@Configuration
public class SecurityConfig {

	/**
	 * Builds the {@code keycloak} {@link ClientRegistration} by hand instead of letting Spring
	 * Boot auto-configure it from {@code spring.security.oauth2.client.*} properties, so that
	 * {@code end_session_endpoint} can be supplied directly.
	 *
	 * <p>Normally that endpoint comes from OIDC discovery ({@code issuer-uri}), which this
	 * service deliberately doesn't configure (see {@code application.yml}'s comments on why) —
	 * without it, {@link OidcClientInitiatedLogoutSuccessHandler} silently finds
	 * no {@code end_session_endpoint} and redirects straight to {@code postLogoutRedirectUri},
	 * skipping Keycloak's own logout entirely and leaving its SSO session alive. Supplying it
	 * here, via {@code providerConfigurationMetadata}, fixes that without re-introducing the
	 * discovery call the missing {@code issuer-uri} was avoiding in the first place.</p>
	 *
	 * @param clientId this service's own Keycloak client id.
	 * @param clientSecret this service's own Keycloak client secret.
	 * @param keycloakBaseUrl the internal Keycloak address (token exchange, ID token signature
	 *                        verification).
	 * @param keycloakBrowserBaseUrl the browser-facing Keycloak address (authorization and
	 *                                end-session redirects).
	 * @param realm the Keycloak realm.
	 * @return a repository holding just this one registration.
	 */
	@Bean
	public ClientRegistrationRepository clientRegistrationRepository(
			@Value("${dlb.bff.oauth2-client-id}") String clientId,
			@Value("${dlb.bff.oauth2-client-secret}") String clientSecret,
			@Value("${dlb.bff.keycloak-base-url}") String keycloakBaseUrl,
			@Value("${dlb.bff.keycloak-browser-base-url}") String keycloakBrowserBaseUrl,
			@Value("${dlb.bff.keycloak-realm:dialoguebranch}") String realm) {
		String realmPath = "realms/" + realm + "/protocol/openid-connect/";

		ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
				.clientId(clientId)
				.clientSecret(clientSecret)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.scope("openid", "profile", "email")
				.authorizationUri(keycloakBrowserBaseUrl + realmPath + "auth")
				.tokenUri(keycloakBaseUrl + realmPath + "token")
				.jwkSetUri(keycloakBaseUrl + realmPath + "certs")
				.userNameAttributeName("preferred_username")
				.providerConfigurationMetadata(
						Map.of("end_session_endpoint", keycloakBrowserBaseUrl + realmPath + "logout"))
				.build();

		return new InMemoryClientRegistrationRepository(registration);
	}

	/**
	 * Overrides Boot's default {@code InMemoryOAuth2AuthorizedClientRepository} — a plain JVM heap
	 * map — with a session-backed one. The access/refresh tokens live here, separately from the
	 * {@code SecurityContext} that {@code HttpSession} already persists to MariaDB; without this
	 * override, identity would survive a redeploy but every {@code /api/**}/{@code /whoami} call
	 * afterward would throw {@code ClientAuthorizationRequiredException} and silently bounce the
	 * user back through Keycloak login.
	 *
	 * @return a session-backed authorized-client store instead of Boot's in-memory default.
	 */
	@Bean
	public OAuth2AuthorizedClientRepository authorizedClientRepository() {
		return new HttpSessionOAuth2AuthorizedClientRepository();
	}

	/**
	 * Configures the security filter chain: session-cookie login against Keycloak (with PKCE
	 * even though the client is confidential, matching current OAuth 2.1 guidance), CSRF
	 * protection using the standard SPA cookie recipe, a plain 401 (rather than a redirect) for
	 * unauthenticated XHR/fetch calls to {@code /api/**} and {@code /whoami}, and RP-initiated
	 * logout.
	 *
	 * @param http the {@link HttpSecurity} to configure.
	 * @param clientRegistrationRepository the registered OAuth2 client(s), used to build the
	 *                                      RP-initiated logout redirect.
	 * @param postLoginRedirectUrl where to send the browser after a successful login, or a
	 *                             successful logout (the two cases need the exact same value: the
	 *                             SPA's own origin) — see the property's own comment in
	 *                             {@code application.yml}.
	 * @param sessionMaxAge absolute cap on session age, see {@link SessionMaxAgeFilter}.
	 * @param cookieSerializer writes the {@code SESSION} cookie, reused by
	 *                         {@link SessionCookieRefreshFilter} to re-issue it on every request.
	 * @return the configured {@link SecurityFilterChain}.
	 * @throws Exception if the security configuration cannot be built.
	 */
	@Bean
	public SecurityFilterChain filterChain(
			HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository,
			@Value("${dlb.bff.post-login-redirect-url:/}") String postLoginRedirectUrl,
			@Value("${dlb.bff.session-max-age}") Duration sessionMaxAge,
			CookieSerializer cookieSerializer)
			throws Exception {

		OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler =
				new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
		// Unlike postLoginRedirectUrl's plain 302 (which the browser resolves relative to its own
		// origin), this is sent as a literal post_logout_redirect_uri to Keycloak, which requires
		// an exact match against a registered absolute URI — plain "/" fails with "Invalid
		// redirect uri". If postLoginRedirectUrl is still its default "/" (same-origin
		// deployments), "{baseUrl}" resolves to the right origin via forwarded headers instead. If
		// it's been overridden (two-origin local dev, BFF and SPA dev server on different ports),
		// "{baseUrl}" would resolve to this BFF's own origin — wrong — so reuse that override as-is.
		logoutSuccessHandler.setPostLogoutRedirectUri(
				"/".equals(postLoginRedirectUrl) ? "{baseUrl}/" : postLoginRedirectUrl);

		// Spring only adds PKCE automatically for public clients (ClientAuthenticationMethod.NONE);
		// this BFF's client is confidential, but current OAuth 2.1 guidance is to use PKCE for
		// every client, not just ones that lack a client secret.
		DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver =
				new DefaultOAuth2AuthorizationRequestResolver(
						clientRegistrationRepository, "/oauth2/authorization");
		authorizationRequestResolver.setAuthorizationRequestCustomizer(
				OAuth2AuthorizationRequestCustomizers.withPkce());

		http
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
			.requestCache(requestCache -> requestCache.requestCache(noXhrRequestCache()))
			.csrf(csrf -> csrf
				// Standard Spring Security recipe for SPA clients: a JS-readable (non-HttpOnly)
				// XSRF-TOKEN cookie the frontend echoes back as the X-XSRF-TOKEN header on
				// state-changing requests. GET/HEAD/OPTIONS/TRACE are exempt by default.
				.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/actuator/health/**").permitAll()
				// Tomcat forwards response.sendError() (see apiAuthenticationEntryPoint below) to
				// /error internally, re-entering this same filter chain — without this, that
				// forward would itself be treated as an unauthenticated request and redirected to
				// login, silently replacing the 401 the client is supposed to see.
				.requestMatchers("/error").permitAll()
				// Studio's pre-login reachability check (see service-health.js) calls
				// this same public, unauthenticated Web Service endpoint through the proxy —
				// ApiProxyController forwards it without a bearer token when there's no session
				// (see its accessTokenOrNull()). Every other /api/** path stays authenticated.
				.requestMatchers(HttpMethod.GET, "/api/v1/info/all").permitAll()
				.anyRequest().authenticated())
			.oauth2Login(oauth2Login -> oauth2Login
				.authorizationEndpoint(authorizationEndpoint -> authorizationEndpoint
					.authorizationRequestResolver(authorizationRequestResolver))
				// true: always land here after a successful login, ignoring the saved-request
				// fallback entirely — this service has nothing else to redirect back to (no
				// real pages of its own beyond the api/whoami/auth paths, all excluded from the
				// request cache already, see noXhrRequestCache()).
				.defaultSuccessUrl(postLoginRedirectUrl, true))
			.logout(logout -> logout
				.logoutSuccessHandler(logoutSuccessHandler))
			.exceptionHandling(exceptionHandling -> exceptionHandling
				// A fetch/XHR call from Studio to a protected /api/** or /whoami path
				// with no (or an expired) session must not receive oauth2Login's default 302
				// redirect to Keycloak — the browser would transparently follow it and hand the
				// frontend Keycloak's login HTML instead of the 401 it needs to detect "please
				// log in". Only those paths are exempted from the redirect-based entry point; a
				// full top-level navigation still redirects normally.
				.authenticationEntryPoint(apiAuthenticationEntryPoint()));

		// Forces the CSRF token to be loaded (and its cookie written) on every request, not just
		// ones that read CsrfToken explicitly — otherwise XSRF-TOKEN never appears for the client
		// to read on its first, unauthenticated page load. Matches Spring's documented SPA recipe.
		http.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

		// Runs before SessionCookieRefreshFilter: a session killed for exceeding
		// dlb.bff.session-max-age must not have its cookie renewed right after.
		http.addFilterAfter(new SessionMaxAgeFilter(sessionMaxAge), CsrfCookieFilter.class);

		// Re-issues the SESSION cookie on every request so its Max-Age keeps sliding forward like
		// the server-side session's own inactivity timeout does — Spring Session otherwise only
		// writes that cookie once, at session creation, so it would hard-expire regardless of how
		// active the user stayed.
		http.addFilterAfter(new SessionCookieRefreshFilter(cookieSerializer), SessionMaxAgeFilter.class);

		return http.build();
	}

	/**
	 * {@link org.springframework.security.web.access.ExceptionTranslationFilter} saves *every*
	 * request that hits an unauthenticated
	 * {@code AuthenticationException} into the session, including {@code /api/**} and {@code
	 * /whoami} XHR/fetch calls that get a plain 401 (see {@link #apiAuthenticationEntryPoint}) —
	 * not just real page navigations. Left alone, an unauthenticated {@code GET /whoami} the web
	 * client makes on boot (see its src/auth.js) gets remembered as "the page the user wanted",
	 * and {@code SavedRequestAwareAuthenticationSuccessHandler} redirects the browser straight
	 * back to {@code /whoami} after login instead of the app itself. Excluding those two path
	 * patterns here means only a genuine top-level navigation can ever become a resume target.
	 */
	private RequestCache noXhrRequestCache() {
		HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
		RequestMatcher xhrPaths = new OrRequestMatcher(
				new AntPathRequestMatcher("/api/**"), new AntPathRequestMatcher("/whoami"));
		requestCache.setRequestMatcher(new NegatedRequestMatcher(xhrPaths));
		return requestCache;
	}

	private AuthenticationEntryPoint apiAuthenticationEntryPoint() {
		AuthenticationEntryPoint jsonEntryPoint = (request, response, authException) ->
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED);

		LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
		entryPoints.put(new AntPathRequestMatcher("/api/**"), jsonEntryPoint);
		entryPoints.put(new AntPathRequestMatcher("/whoami"), jsonEntryPoint);

		DelegatingAuthenticationEntryPoint delegate = new DelegatingAuthenticationEntryPoint(entryPoints);
		delegate.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/keycloak"));
		return delegate;
	}

	/**
	 * Delegates to {@link XorCsrfTokenRequestAttributeHandler} (Spring's default,
	 * BREACH-resistant token encoding) but only requires the raw token to be resolvable from
	 * either the {@code X-XSRF-TOKEN} header (the normal fetch/XHR case) or the {@code _csrf}
	 * request parameter (the one case that can't attach a header: logout, which has to be a real
	 * top-level form POST rather than a fetch — see the client's src/auth.js for why) — the plain
	 * value the client reads from the {@code XSRF-TOKEN} cookie and echoes back either way, rather
	 * than the masked value the deferred-loading (server-rendered form) flow expects.
	 */
	private static final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

		private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response,
							Supplier<CsrfToken> csrfToken) {
			this.delegate.handle(request, response, csrfToken);
		}

		@Override
		public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
			String headerValue = request.getHeader(csrfToken.getHeaderName());
			String paramValue = request.getParameter(csrfToken.getParameterName());
			return (StringUtils.hasText(headerValue) || StringUtils.hasText(paramValue))
					? super.resolveCsrfTokenValue(request, csrfToken)
					: this.delegate.resolveCsrfTokenValue(request, csrfToken);
		}
	}

	private static final class CsrfCookieFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
										 FilterChain filterChain)
				throws ServletException, IOException {
			CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
			if (csrfToken != null) {
				csrfToken.getToken();
			}
			filterChain.doFilter(request, response);
		}
	}

	/**
	 * Server-side counterpart to Keycloak's {@code client.session.max.lifespan} (see
	 * {@code sync-realm.py}'s handling of {@code DLB_BFF_SESSION_MAX_AGE}): an absolute cap on
	 * session age that, unlike {@code dlb.bff.session-timeout}'s sliding inactivity window,
	 * activity can't extend. {@link HttpSession#getCreationTime()} is what makes this possible,
	 * Spring Session tracks it separately from the last-accessed time the sliding timeout relies
	 * on, so it stays fixed regardless of how active the session has been.
	 */
	private static final class SessionMaxAgeFilter extends OncePerRequestFilter {

		private final Duration maxAge;

		SessionMaxAgeFilter(Duration maxAge) {
			this.maxAge = maxAge;
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
										 FilterChain filterChain)
				throws ServletException, IOException {
			HttpSession session = request.getSession(false);
			if (session != null) {
				Duration age = Duration.ofMillis(System.currentTimeMillis() - session.getCreationTime());
				if (age.compareTo(maxAge) > 0) {
					session.invalidate();
				}
			}
			filterChain.doFilter(request, response);
		}
	}

	/**
	 * See the filter chain's own comment. Uses {@code getSession(false)}: never creates a session
	 * just to set this cookie on an anonymous request.
	 */
	private static final class SessionCookieRefreshFilter extends OncePerRequestFilter {

		private final CookieSerializer cookieSerializer;

		SessionCookieRefreshFilter(CookieSerializer cookieSerializer) {
			this.cookieSerializer = cookieSerializer;
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
										 FilterChain filterChain)
				throws ServletException, IOException {
			HttpSession session = request.getSession(false);
			if (session != null) {
				cookieSerializer.writeCookieValue(
						new CookieSerializer.CookieValue(request, response, session.getId()));
			}
			filterChain.doFilter(request, response);
		}
	}
}
