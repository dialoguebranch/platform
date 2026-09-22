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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * Wires up what {@link ApiProxyController} and {@link WhoAmIController} need: a manager that
 * fetches (and, via {@link OAuth2AuthorizedClientProviderBuilder#refreshToken()}, transparently
 * refreshes) the session's stored access token, and the {@link RestClient} used to actually call
 * the Dialogue Branch Web Service.
 *
 * @author Dennis Hofs
 */
@Configuration
public class ProxyConfig {

	/**
	 * @param clientRegistrationRepository the registered OAuth2 client(s).
	 * @param authorizedClientRepository the store (HTTP session) authorized clients are read
	 *                                   from and written back to after a refresh.
	 * @return a manager that owns token refresh entirely server-side.
	 */
	@Bean
	public OAuth2AuthorizedClientManager authorizedClientManager(
			ClientRegistrationRepository clientRegistrationRepository,
			OAuth2AuthorizedClientRepository authorizedClientRepository) {
		OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
				.authorizationCode()
				.refreshToken()
				.build();
		DefaultOAuth2AuthorizedClientManager manager = new DefaultOAuth2AuthorizedClientManager(
				clientRegistrationRepository, authorizedClientRepository);
		manager.setAuthorizedClientProvider(provider);
		return manager;
	}

	/**
	 * @param baseUrl the internal address of the Dialogue Branch Web Service, e.g.
	 *                {@code http://localhost:8089/dlb-web-service}.
	 * @return the {@link RestClient} {@link ApiProxyController} forwards {@code /api/**} calls
	 * through.
	 */
	@Bean
	public RestClient apiRestClient(@Value("${dlb.bff.api-base-url}") String baseUrl) {
		// ApiProxyController forwards the incoming request's raw, already-encoded path and query
		// string verbatim (via HttpServletRequest#getRequestURI/#getQueryString). The default
		// encoding mode would encode them a second time — a query value the caller already
		// percent-encoded (e.g. "Europe%2FLisbon") would have its literal "%" re-encoded to
		// "%25", corrupting it into "Europe%252FLisbon" by the time it reaches the Web Service.
		// EncodingMode.NONE passes the already-encoded bytes through untouched, matching what
		// this proxy is actually meant to do.
		DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(baseUrl);
		uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
		return RestClient.builder().uriBuilderFactory(uriBuilderFactory).build();
	}
}
