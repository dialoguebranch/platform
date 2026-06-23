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

package com.dialoguebranch.web.service;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;

/**
 * A servlet {@link Filter} that adds CORS headers to every HTTP response, allowing cross-origin
 * requests from any origin. This enables browser-based client applications hosted on a different
 * domain to communicate with the Dialogue Branch Web Service.
 *
 * @author Harm op den Akker
 */
@Component
public class CorsFilter implements Filter {

	/** Creates a new {@link CorsFilter} instance. */
	public CorsFilter() { }

	/**
	 * Called by the servlet container when this filter is placed into service. No initialization
	 * logic is required for this filter.
	 *
	 * @param filterConfig the filter configuration object provided by the servlet container.
	 */
	@Override
	public void init(FilterConfig filterConfig) {
	}

	/**
	 * Adds CORS response headers to every request and passes the request along the filter chain.
	 *
	 * @param request the servlet request.
	 * @param response the servlet response.
	 * @param chain the filter chain to pass the request/response to.
	 * @throws IOException if an I/O error occurs during processing.
	 * @throws ServletException if a servlet error occurs during processing.
	 */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain)
			throws IOException, ServletException {
		HttpServletResponse httpResponse = (HttpServletResponse)response;
		httpResponse.setHeader("Access-Control-Allow-Origin", "*");
		httpResponse.setHeader("Access-Control-Allow-Methods",
				"GET, HEAD, POST, PUT, DELETE, OPTIONS");
		httpResponse.setHeader("Access-Control-Allow-Headers",
			"Authorization, " +
			"Content-Type, " +
			"Accept, " +
			"Accept-Language, " +
			"X-Requested-With, " +
			"X-Auth-Token, " +
			"ngrok-skip-browser-warning, " +
			"User-Agent");
		chain.doFilter(request, response);
	}

	/**
	 * Called by the servlet container when this filter is taken out of service. No cleanup logic
	 * is required for this filter.
	 */
	@Override
	public void destroy() {
	}
}
