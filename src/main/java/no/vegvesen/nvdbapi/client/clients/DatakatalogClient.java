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
import no.vegvesen.nvdbapi.client.gson.AttributeTypeParser;
import no.vegvesen.nvdbapi.client.gson.FeatureTypeParser;
import no.vegvesen.nvdbapi.client.gson.DatakatalogVersionParser;
import no.vegvesen.nvdbapi.client.model.datakatalog.*;
import no.vegvesen.nvdbapi.client.util.Stopwatch;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.Objects.isNull;
import static no.vegvesen.nvdbapi.client.gson.GsonUtil.rt;
import static org.apache.hc.core5.http.io.support.ClassicRequestBuilder.get;

public class DatakatalogClient extends AbstractJerseyClient {
    private static final Logger LOG = LoggerFactory.getLogger(DatakatalogClient.class);

    private Map<String, DataType> dataTypes;

    protected DatakatalogClient(HttpHost baseUrl, HttpClient client) {
        super(baseUrl, client);
    }

    public Version getVersion() {
        return JerseyHelper.executeOptional(getClient(), start(), get("vegobjekttyper/versjon"))
                           .map(JsonElement::getAsJsonObject)
                           .map(DatakatalogVersionParser::parseVersion)
                           .get();
    }

    public List<DataType> getDataTypes() {
        JsonElement types = JerseyHelper.execute(getClient(), start(), get("vegobjekttyper/datatyper"));
        return StreamSupport.stream(types.getAsJsonArray().spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .map(rt(AttributeTypeParser::parseDataType))
                .collect(Collectors.toList());
    }

    public Map<String, DataType> getDataTypeMap() {
        return getDataTypes().stream()
                             .collect(Collectors.toMap(DataType::getName, Function.identity()));
    }

    public List<Unit> getUnits() {
        JsonElement units = JerseyHelper.execute(getClient(), start(), get("vegobjekttyper/enheter"));
        return StreamSupport.stream(units.getAsJsonArray().spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .map(rt(AttributeTypeParser::parseUnit))
                .collect(Collectors.toList());
    }

    public List<FeatureTypeCategory> getCategories() {
        JsonElement units = JerseyHelper.execute(getClient(), start(), get("vegobjekttyper/kategorier"));
        return StreamSupport.stream(units.getAsJsonArray().spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .map(rt(FeatureTypeParser::parseCategory))
                .collect(Collectors.toList());
    }

    public List<AttributeTypeCategory> getAttributeTypeCategories() {
        JsonElement units = JerseyHelper.execute(getClient(), start(), get("vegobjekttyper/egenskapstypekategorier"));
        return StreamSupport.stream(units.getAsJsonArray().spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .map(rt(AttributeTypeParser::parseCategory))
                .collect(Collectors.toList());
    }

    public Map<Integer, Unit> getUnitMap() {
        return getUnits().stream()
                         .collect(Collectors.toMap(Unit::getId, Function.identity()));
    }

    public Optional<AttributeType> getAttributeType(int typeId) {
        initDataTypes();
        return JerseyHelper.executeOptional(getClient(), start(), get("vegobjekttyper/egenskapstyper/" + typeId))
                           .map(JsonElement::getAsJsonObject)
                           .map(o -> AttributeTypeParser.parse(dataTypes, o));
    }

    private void initDataTypes() {
        if(isNull(this.dataTypes)) {
            this.dataTypes = getDataTypeMap();
        }
    }

    public Datakatalog getDatakalog() {
        Version v = getVersion();
        List<Unit> units = getUnits();
        Map<String, DataType> dataTypes = getDataTypeMap();

        List<FeatureType> featureTypes = getFeatureTypes(Include.ALL);
        return new Datakatalog(v, featureTypes, units, dataTypes);
    }

    public List<FeatureType> getFeatureTypes(Include... informationToInclude) {
        return getFeatureTypes(-1, informationToInclude);
    }

    public List<FeatureType> getFeatureTypes(int category, Include... informationToInclude) {
        ClassicRequestBuilder builder = get("/vegobjekttyper");
        String includeArgument = getIncludeArgument(false, informationToInclude);
        if (includeArgument != null) builder.addParameter("inkluder", includeArgument);
        if (category > 0) builder.addParameter("kategori", String.valueOf(category));

        Stopwatch sw = Stopwatch.createStarted();
        JsonArray array = JerseyHelper.executeOptional(getClient(), start(), builder)
                                      .map(JsonElement::getAsJsonArray)
                                      .get();
        long requestTime = sw.stop().elapsedMillis();
        sw = Stopwatch.createStarted();
        initDataTypes();

        List<FeatureType> types = StreamSupport.stream(array.getAsJsonArray().spliterator(), false)
            .map(JsonElement::getAsJsonObject)
            .map(rt(o -> FeatureTypeParser.parse(this.dataTypes, o)))
            .collect(Collectors.toList());

        long parsingTime = sw.stop().elapsedMillis();
        LOG.debug("Request execution took {} ms. Request parsing took {} ms. Total: {} ms.", requestTime, parsingTime, requestTime + parsingTime);
        return types;
    }

    public Optional<FeatureType> getFeatureType(int typeId, Include... informationToInclude) {
        ClassicRequestBuilder builder = get("/vegobjekttyper/" + typeId);

        String includeArgument = getIncludeArgument(true, informationToInclude);
        if (includeArgument != null) builder.addParameter("inkluder", includeArgument);
        initDataTypes();
        return JerseyHelper.executeOptional(getClient(), start(), builder)
                           .map(JsonElement::getAsJsonObject)
                           .map(rt(o -> FeatureTypeParser.parse(this.dataTypes, o)));
    }

    private static String getIncludeArgument(boolean singleRequest, Include... informationToInclude) {
        Set<Include> values = informationToInclude.length > 0 ? new HashSet<>(Arrays.asList(informationToInclude)) : Collections.emptySet();

        // Defaults
        if (values.isEmpty()) {
            return singleRequest ? "alle" : null;
        }

        // "All" trumps any other values
        if (values.contains(Include.ALL)) {
            return Include.ALL.value;
        }

        return values.stream()
                .map(i -> i.value)
                .collect(Collectors.joining(","));
    }

    public enum Include {
        ATTRIBUTES("egenskapstyper"),
        ASSOCIATIONS("relasjonstyper"),
        GUIDANCE_PARAMETERS("styringsparametere"),
        LOCATIONAL("stedfesting"),
        ALL("alle");

        private final String value;

        Include(String value) {
            this.value = value;
        }
    }
}
