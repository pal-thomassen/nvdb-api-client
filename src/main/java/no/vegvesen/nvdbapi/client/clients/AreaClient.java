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
import com.google.gson.JsonObject;
import no.vegvesen.nvdbapi.client.clients.util.JerseyHelper;
import no.vegvesen.nvdbapi.client.gson.AreaParser;
import no.vegvesen.nvdbapi.client.model.Projection;
import no.vegvesen.nvdbapi.client.model.areas.*;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static no.vegvesen.nvdbapi.client.gson.GsonUtil.rt;
import static org.apache.hc.core5.http.io.support.ClassicRequestBuilder.get;

public class AreaClient extends AbstractJerseyClient {

    protected AreaClient(HttpHost baseUrl, HttpClient client) {
        super(baseUrl, client);
    }

    public List<Municipality> getMunicipalities(boolean includeBoundingBox, boolean includeCenterPoint, boolean includeObjectLink, Projection projection) {
        return getAreas(includeBoundingBox, includeCenterPoint, includeObjectLink, projection, get("omrader/kommuner"))
            .map(rt(AreaParser::parseMun))
            .collect(Collectors.toList());
    }

    public List<Municipality> getMunicipalities() {
        return getMunicipalities(true, true, false, null);
    }

    public List<County> getCountys(boolean includeBoundingBox, boolean includeCenterPoint, boolean includeObjectLink, Projection projection) {
        return getAreas(includeBoundingBox, includeCenterPoint, includeObjectLink, projection, get("omrader/fylker"))
            .map(rt(AreaParser::parseCounty))
            .collect(Collectors.toList());
    }

    public List<County> getCountys() {
        return getCountys(true, true, false, null);
    }

    public List<Route> getNationalRoutes(boolean includeObjectLink) {
        ClassicRequestBuilder builder = get("omrader/riksvegruter");

        if (includeObjectLink) builder.addParameter("inkluder", getIncludeParameter(false, false, true));

        return getAreas(builder)
            .map(rt(AreaParser::parseRoute))
            .collect(Collectors.toList());
    }

    public List<Route> getNationalRoutes() {
        return getNationalRoutes(false);
    }

    public List<ContractArea> getContractAreas(boolean includeObjectLink) {
        ClassicRequestBuilder builder = get("omrader/kontraktsomrader");

        if (includeObjectLink) builder.addParameter("inkluder", getIncludeParameter(false, false, true));

        return getAreas(builder)
            .map(rt(AreaParser::parseContractArea))
            .collect(Collectors.toList());
    }

    public List<ContractArea> getContractAreas() {
        return getContractAreas(false);
    }

    private Stream<JsonObject> getAreas(boolean includeBoundingBox,
                                        boolean includeCenterPoint,
                                        boolean includeObjectLink,
                                        Projection projection, ClassicRequestBuilder target) {
        if (projection != null) {
            target = target.addParameter("srid", String.valueOf(projection.getSrid()));
        }

        if (includeCenterPoint || includeBoundingBox || includeObjectLink) {
            target = target.addParameter("inkluder", getIncludeParameter(includeBoundingBox, includeCenterPoint, includeObjectLink));
        }

        return getAreas(target);
    }

    private Stream<JsonObject> getAreas(ClassicRequestBuilder target) {
        JsonElement e = JerseyHelper.execute(getClient(), start(), target);
        JsonArray a = e.getAsJsonArray();
        return StreamSupport.stream(a.spliterator(), false).map(JsonElement::getAsJsonObject);
    }

    private static String getIncludeParameter(boolean includeBoundingbox, boolean includeCenterPoint, boolean includeObjectLink) {
        List<String> inkluder = new ArrayList<>();

        if (includeBoundingbox) {
            inkluder.add("kartutsnitt");
        }
        if (includeBoundingbox) {
            inkluder.add("polygon");
        }

        if (includeCenterPoint) {
            inkluder.add("senterpunkt");
        }

        if (includeObjectLink) {
            inkluder.add("vegobjekt");
        }

        return String.join(",", inkluder);
    }

}
