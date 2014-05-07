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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.util.concurrent.SettableFuture;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

/**
 * TALOCache Servlet that provides transparent caching of web responses.
 *
 * @author Dustin Sweigart <dustin@swigg.net>
 */
public class TaloCacheServlet extends ProxyServlet.Transparent {
    static private final Logger LOGGER = LoggerFactory.getLogger(TaloCacheServlet.class);

    private final ConcurrentMap<RequestIdentity, SettableFuture<ResponseHolder>> cache = new ConcurrentHashMap<>(8, 0.9f, 1);

    private Predicate<HttpServletRequest> serveFromCache;
    private Predicate<Response> saveToCache;

    protected void service(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        // check if we should even try and serve from the cache
        Predicate<HttpServletRequest> serveFromCache = this.serveFromCachePredicate();
        if (!serveFromCache.apply(request)) {
            super.service(request, response);
            return;
        }

        RequestIdentity requestIdentity = new RequestIdentity(request);
        SettableFuture<ResponseHolder> settableFuture = null;
        SettableFuture<ResponseHolder> responseHolderSettableFuture = SettableFuture.create();

        // add a cache entry for this request if one doesn't already exists
        settableFuture = cache.putIfAbsent(requestIdentity, responseHolderSettableFuture);
        if (settableFuture != null) {
            this.writeCachedResponse(settableFuture, request, response);
            return;
        }

        // service the request
        request.setAttribute("requestIdentity", requestIdentity);
        super.service(request, response);
    }

    /**
     * Create a {@link Predicate} for if the {@link HttpServletRequest} is applicable to be served from the cache.
     */
    private Predicate<HttpServletRequest> serveFromCachePredicate() {
        if (this.serveFromCache != null) {
            return serveFromCache;
        }

        Predicate<HttpServletRequest> getPredicate = new Predicate<HttpServletRequest>() {
            @Override
            public boolean apply(HttpServletRequest request) {
                return request.getMethod().equals("GET");
            }
        };

        Predicate<HttpServletRequest> headPredicate = new Predicate<HttpServletRequest>() {
            @Override
            public boolean apply(HttpServletRequest request) {
                return request.getMethod().equals("HEAD");
            }
        };

        this.serveFromCache = Predicates.or(getPredicate, headPredicate);
        return serveFromCachePredicate();
    }

    /**
     * Create a {@link Predicate} for if the {@link HttpServletResponse} is applicable to be cached to be used in
     * the future.
     */
    private Predicate<Response> saveToCachePredicate() {
        if (this.saveToCache != null) {
            return saveToCache;
        }

        Predicate<Response> successPredicate = new Predicate<Response>() {
            @Override
            public boolean apply(Response response) {
                return response.getStatus() >= 200 && response.getStatus() <= 399;
            }
        };

        this.saveToCache = successPredicate;
        return saveToCachePredicate();
    }

    private void writeCachedResponse(final SettableFuture<ResponseHolder> settableFuture, final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final AsyncContext asyncContext = request.startAsync();
        asyncContext.start(new Runnable() {
            @Override
            public void run() {
                Response originalResponse = null;
                try {
                    originalResponse = settableFuture.get().getResponse();
                    response.setStatus(originalResponse.getStatus());

                    for (HttpField httpField : originalResponse.getHeaders()) {
                        response.setHeader(httpField.getName(), httpField.getValue());
                    }

                    response.getWriter().write(settableFuture.get().getBody());
                    response.getWriter().flush();
                    response.getWriter().close();
                } catch (InterruptedException | ExecutionException | IOException e) {
                    LOGGER.error("Error writing cached response.", e);
                }

                asyncContext.complete();
            }
        });
    }

    @Override
    protected void customizeProxyRequest(final Request proxyRequest, final HttpServletRequest request) {
        final RequestIdentity requestIdentity = (RequestIdentity) request.getAttribute("requestIdentity");
        ProxyResponseListener proxyResponseListener = new ProxyResponseListener(requestIdentity, cache);
        proxyRequest.onResponseContent(proxyResponseListener);
        proxyRequest.onResponseSuccess(proxyResponseListener);

        // remove from cache if invalid to be served from in the future
        final Predicate<Response> saveToCache = this.saveToCachePredicate();
        proxyRequest.onComplete(new Response.CompleteListener() {
            @Override
            public void onComplete(Result result) {
                if (result.isFailed() || !saveToCache.apply(result.getResponse())) {
                    cache.remove(requestIdentity);
                }
            }
        });
    }
}
