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

import com.google.common.base.Charsets;
import com.google.common.util.concurrent.SettableFuture;
import org.eclipse.jetty.client.api.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentMap;

/**
 * Response listener for storing the response.
 *
 * @author Dustin Sweigart <dustin@swigg.net>
 */
public class ProxyResponseListener implements Response.SuccessListener, Response.ContentListener {
    private final RequestIdentity requestIdentity;
    private final ConcurrentMap<RequestIdentity, SettableFuture<ResponseHolder>> cache;
    private final ByteArrayOutputStream outputStream;

    public ProxyResponseListener(final RequestIdentity requestIdentity, final ConcurrentMap<RequestIdentity, SettableFuture<ResponseHolder>> cache) {
        this.requestIdentity = requestIdentity;
        this.cache = cache;
        this.outputStream = new ByteArrayOutputStream();
    }

    @Override
    public void onContent(Response response, ByteBuffer content) {
        byte[] buffer;
        int offset;
        int length = content.remaining();
        if (content.hasArray())
        {
            buffer = content.array();
            offset = content.arrayOffset();
        }
        else
        {
            buffer = new byte[length];
            content.get(buffer);
            offset = 0;
        }

        try {
            outputStream.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSuccess(Response response) {
        ResponseHolder responseHolder = new ResponseHolder(response, new String(outputStream.toByteArray(), Charsets.UTF_8));
        cache.get(requestIdentity).set(responseHolder);
    }
}
