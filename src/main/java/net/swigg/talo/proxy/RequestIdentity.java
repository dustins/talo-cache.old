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

import com.google.common.base.Objects;
import com.google.common.collect.Ordering;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

/**
 * Identity for requests that provides basic normalization
 *
 * @author Dustin Sweigart <dustin@swigg.net>
 */
public class RequestIdentity {
    private HttpServletRequest request;

    private final String requestUri;
    private int headersHash     = 0;
    private int queryStringHash = 0;

    public RequestIdentity(HttpServletRequest request) {
        this.request = request;
        this.requestUri = request.getRequestURI();
        this.headersHash = this.computeHeaderHash();
    }

    private int computeHeaderHash() {
        checkState(request != null);

        Ordering order = Ordering.natural().nullsFirst();
        List<String> headerNames = order.sortedCopy(Collections.list(request.getHeaderNames()));
        String[] headers = new String[headerNames.size()*2];

        for (int i=0; i < headerNames.size(); i++) {
            headers[i*2] = headerNames.get(i);
            headers[i*2+1] = request.getHeader(headerNames.get(i));
        }

        return Arrays.hashCode(headers);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(requestUri, headersHash, queryStringHash);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final RequestIdentity other = (RequestIdentity) obj;
        return Objects.equal(this.requestUri, other.requestUri) &&
                Objects.equal(this.headersHash, other.headersHash) &&
                Objects.equal(this.queryStringHash, other.queryStringHash);
    }
}
