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

package no.vegvesen.nvdbapi.client.clients.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import no.vegvesen.nvdbapi.client.exceptions.ApiError;
import no.vegvesen.nvdbapi.client.exceptions.ClientException;
import no.vegvesen.nvdbapi.client.exceptions.JsonExceptionParser;
import org.apache.commons.codec.Charsets;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public class JerseyHelper {
    private static final Logger logger = LoggerFactory.getLogger(JerseyHelper.class);
    public static final String MEDIA_TYPE = "application/vnd.vegvesen.nvdb-v3+json";

    private JerseyHelper() {}

    public static boolean isSuccess(ClassicHttpResponse response) {
        return 200 <= response.getCode() && response.getCode() < 300;
    }

    public static ClientException parseError(ClassicHttpResponse response) throws IOException, ParseException {
        List<ApiError> errors = JsonExceptionParser.parse(EntityUtils.toString(response.getEntity()));
        return new ClientException(response.getCode(), errors);
    }

    public static <T> T execute(Invocation inv, Class<T> responseClass) {
        try {
            return inv.invoke(responseClass);
        } catch (WebApplicationException ex) {
            logger.error("Got error on: {}", ex.getResponse().toString());
            throw parseError(ex.getResponse());
        }
    }

    public static JsonElement execute(WebTarget target) {
        return execute(target, MEDIA_TYPE);
    }

    public static JsonElement execute(WebTarget target, String mediaType) {
        Invocation invocation = target.request().accept(mediaType).buildGet();
        try(Response response = execute(invocation, Response.class)) {

            if (!isSuccess(response)) {
                throw parseError(response);
            }

            return new JsonParser().parse(
                    new InputStreamReader((InputStream) response.getEntity(), StandardCharsets.UTF_8));
        }
    }

    public static <T> T execute(Invocation inv, GenericType<T> responseType) {
        try {
            return inv.invoke(responseType);
        } catch (WebApplicationException ex) {
            logger.error("Got error on: {}", ex.getResponse().toString());
            throw parseError(ex.getResponse());
        }
    }

    public static Optional<JsonElement> executeOptional(HttpClient client, HttpHost start, ClassicRequestBuilder target) {
        try(ClassicHttpResponse response = client.execute(start, target.build())) {

            if (!isSuccess(response)) {
                if (response.getCode() == 404) {
                    return Optional.empty();
                }
                throw parseError(response);
            }

            if (response.getCode() == 204) {
                return Optional.empty();
            }
            try (InputStream is = response.getEntity().getContent()) {
                return Optional.of(JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)));
            } catch (Exception e) {
                throw new RuntimeException("Error parsing response", e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonElement execute(HttpClient client, HttpHost start, ClassicRequestBuilder target) {
        try (ClassicHttpResponse response = client.execute(start, target.build())) {
            if (!isSuccess(response)) {
                throw parseError(response);
            }
            return JsonParser.parseReader(new InputStreamReader(response.getEntity().getContent(), Charsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
