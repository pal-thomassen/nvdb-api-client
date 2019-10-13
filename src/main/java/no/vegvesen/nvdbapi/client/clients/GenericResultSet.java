/*
 * Copyright (c) 2015-2017, Statens vegvesen
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.vegvesen.nvdbapi.client.clients;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import no.vegvesen.nvdbapi.client.clients.util.JerseyHelper;
import no.vegvesen.nvdbapi.client.gson.GsonUtil;
import no.vegvesen.nvdbapi.client.model.Page;
import no.vegvesen.nvdbapi.client.model.ResultSet;
import no.vegvesen.nvdbapi.client.util.ResultSetCollector;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GenericResultSet<T> implements ResultSet<T> {
    private static final Logger logger = LoggerFactory.getLogger(GenericResultSet.class);

    private final HttpClient client;
    private final HttpHost baseUrl;
    private final ClassicRequestBuilder builder;
    private final Function<JsonObject, T> parser;
    private Page currentPage;
    private String token;
    private boolean hasNext = true;

    protected GenericResultSet(HttpClient client,
                               HttpHost baseUrl,
                               ClassicRequestBuilder builder,
                               Page currentPage,
                               Function<JsonObject, T> parser) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.builder = builder;
        this.parser = parser;
        this.currentPage = currentPage;
    }

    public List<T> getAll() {
        return ResultSetCollector.getAll(this);
    }

    public Stream<T> stream() {
        return getAll().stream();
    }

    @Override
    public boolean hasNext() {
        return hasNext;
    }

    @Override
    public List<T> next() {
        if (!hasNext()) {
            throw new IllegalStateException("No more results!");
        }

        // Setup and execute request
        if (currentPage != null) actualTarget = applyPage(currentPage, baseTarget);


        JsonObject currentResponse = JerseyHelper.execute(client, baseUrl, builder).getAsJsonObject();
        int numTotal = GsonUtil.parseIntMember(currentResponse, "metadata.antall");
        int numReturned = GsonUtil.parseIntMember(currentResponse, "metadata.returnert");
//        int numPerPage = GsonUtil.parseIntMember(currentResponse, "metadata.sidestørrelse");
        logger.debug("Result size returned was {}.", numTotal);
        logger.debug("Results in page returned was {}.", numReturned);
//        logger.debug("Page size returned was {}.", numPerPage);

        if (logger.isTraceEnabled()){
            logger.trace("Response: {}", currentResponse.toString());
        }

        // Prepare next request
        String nextToken = GsonUtil.getNode(currentResponse, "metadata.neste.start")
                .map(JsonElement::getAsString)
                .orElse(null);
        logger.debug("last token: {} next token: {}", token, nextToken);
        // no next page if last token and next token are equal
        hasNext = nextToken != null && (!nextToken.equals(token));
        token = nextToken;
        currentPage = currentPage.withStart(token);

        if (!hasNext) {
            logger.debug("Result set exhausted.");
        }
        return StreamSupport
                .stream(currentResponse.getAsJsonArray("objekter").spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .map(parser)
                .collect(Collectors.toList());
    }

    public String nextToken() {
        return token;
    }

    static WebTarget applyPage(Page page, WebTarget target) {
        if (Objects.nonNull(page.getCount())) {
            target = target.queryParam("antall", page.getCount());
        }
        if (page.getStart().isPresent()) {
            target = target.queryParam("start", page.getStart().get());
        }

        return target;
    }

}
