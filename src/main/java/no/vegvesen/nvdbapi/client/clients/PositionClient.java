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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import no.vegvesen.nvdbapi.client.clients.util.JerseyHelper;
import no.vegvesen.nvdbapi.client.gson.PlacementParser;
import no.vegvesen.nvdbapi.client.model.Position;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static no.vegvesen.nvdbapi.client.gson.GsonUtil.rt;
import static org.apache.hc.core5.http.io.support.ClassicRequestBuilder.get;

public class PositionClient extends AbstractJerseyClient {

    public PositionClient(HttpHost baseUrl, HttpClient client) {
        super(baseUrl, client);
    }

    public Position getPlacement(PositionRequest req) {
        ClassicRequestBuilder builder = get("/posisjon");

        req.getNorth().ifPresent(v -> builder.addParameter("nord", String.valueOf(v)));
        req.getEast().ifPresent(v -> builder.addParameter("ost", String.valueOf(v)));
        req.getLat().ifPresent(v -> builder.addParameter("lat", String.valueOf(v)));
        req.getLon().ifPresent(v -> builder.addParameter("lon", String.valueOf(v)));
        req.getProjection().ifPresent(v -> builder.addParameter("srid", String.valueOf(v.getSrid())));
        req.getMaxResults().ifPresent(v -> builder.addParameter("maks_antall", String.valueOf(v)));
        req.getMaxDistance().ifPresent(v -> builder.addParameter("maks_avstand", String.valueOf(v)));
        req.getConnectionLinks().ifPresent(v -> builder.addParameter("konnekteringslenker", String.valueOf(v)));
        req.getDetailedLinks().ifPresent(v -> builder.addParameter("detaljerte_lenker", String.valueOf(v)));
        req.getRoadRefFilters().ifPresent(v -> builder.addParameter("vegsystemreferanse", v));

        JsonArray results = JerseyHelper.execute(getClient(), start(), builder).getAsJsonArray();

        List<Position.Result> collect =
                StreamSupport.stream(results.spliterator(), false)
                             .map(JsonElement::getAsJsonObject)
                             .map(rt(PlacementParser::parsePosition))
                             .collect(Collectors.toList());
        return new Position(collect);
    }

}
