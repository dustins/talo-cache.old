/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Dustin Sweigart
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.swigg.talo.proxy;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * TALOCache Servlet that provides transparent caching of web responses.
 *
 * @author Dustin Sweigart <dustin@swigg.net>
 */
public class TaloCacheServlet extends ProxyServlet.Transparent {
    static private final Logger LOGGER = LoggerFactory.getLogger(TaloCacheServlet.class);

    private Map<RequestIdentity, ResponseHolder> cache = Maps.newConcurrentMap();
    private Map<RequestIdentity, SettableFuture<ResponseHolder>> activeRequests = Maps.newConcurrentMap();

    protected void service(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        if (!request.getMethod().equals("GET") && !request.getMethod().equals("HEAD")) {
            super.service(request, response);
            return;
        }

        RequestIdentity requestIdentity = new RequestIdentity(request);
        if (cache.containsKey(requestIdentity)) {
            this.writeCachedResponse(cache.get(requestIdentity), response);
            return;
        } else if (activeRequests.containsKey(requestIdentity)) {
            try {
                this.writeCachedResponse(activeRequests.get(requestIdentity).get(), response);
                return;
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        SettableFuture<ResponseHolder> settableFuture = SettableFuture.create();
        activeRequests.put(requestIdentity, settableFuture);

        super.service(request, response);
    }

    private void writeCachedResponse(ResponseHolder responseHolder, HttpServletResponse response) throws IOException {
        Response originalResponse = responseHolder.getResponse();

        response.setStatus(originalResponse.getStatus());
        for (HttpField httpField : originalResponse.getHeaders()) {
            response.setHeader(httpField.getName(), httpField.getValue());
        }
        response.getWriter().write(responseHolder.getBody());
        response.getWriter().flush();
        response.getWriter().close();
    }

    @Override
    protected void customizeProxyRequest(Request proxyRequest, HttpServletRequest request) {
        final RequestIdentity requestIdentity = new RequestIdentity(request);
        final SettableFuture<ResponseHolder> settableFuture = this.activeRequests.get(requestIdentity);
        ProxyResponseListener proxyResponseListener = new ProxyResponseListener(requestIdentity, cache, settableFuture);
        proxyRequest.onResponseContent(proxyResponseListener);
        proxyRequest.onResponseSuccess(proxyResponseListener);
    }
}
