package org.eso.asp.ssap.service;

/*
 * This file is part of SSAPServer.
 *
 * SSAPServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SSAPServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with SSAPServer. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2017 - European Southern Observatory (ESO)
 */

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.fluent.Request;
import org.eso.asp.ssap.util.QueryCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.eso.asp.ssap.domain.ParameterMappings.*;

/**
 * This class implements SSAPService by translating SSA requests into ADQL queries
 * and send them to a TAP service. It is instantiated if ssap.use.tap=true
 *
 * @author Vincenzo Forch&igrave (ESO), vforchi@eso.org, vincenzo.forchi@gmail.com
 */
@Service
@Configurable
@ConditionalOnProperty(value="ssap.use.tap", havingValue = "true")
public class SSAPServiceTAPImpl implements SSAPService {

    private static final Logger log = LoggerFactory.getLogger(SSAPServiceTAPImpl.class);

    @Value("${ssap.tap.timeout:10}")
    private Integer timeoutSeconds;

    @Value("${ssap.tap.url}")
    private String tapURL;

    @Value("${ssap.tap.select.clause:*}")
    public String selectedColumns;

    @Value("${ssap.tap.table:dbo.ssa}")
    public String tapTable;

    @Value("#{${ssap.tap.params.to.columns:{:}}}")
    public Map<String, Object> paramsToColumns;

    @PostConstruct
    public void init() {
        /* if not initialized, map using the UCDs */
        if (paramsToColumns == null || paramsToColumns.size() == 0) {
            try {
                StringBuffer tapRequest = getAdqlURL();

                String query = "SELECT * FROM TAP_SCHEMA.columns WHERE table_name = '" + tapTable + "'";
                tapRequest.append(URLEncoder.encode(query, "ISO-8859-1"));

                String body = Request.Get(tapRequest.toString())
                        .connectTimeout(timeoutSeconds * 1000)
                        .socketTimeout(timeoutSeconds * 1000)
                        .execute().returnContent().asString();
                paramsToColumns = parseFromXML(body);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Object queryData(Map<String, String> params) throws IOException, ParseException {

        StringBuffer tapRequest = getAdqlURL();

        /* query */
        tapRequest.append(createADQLQuery(params));

        /* MAXREC */
        if (params.containsKey(MAXREC)) {
            tapRequest.append("&MAXREC=").append(params.get(MAXREC));
            params.remove(MAXREC);
        }

        return Request.Get(tapRequest.toString())
                .connectTimeout(timeoutSeconds*1000)
                .socketTimeout(timeoutSeconds*1000)
                .execute().returnContent().asString();

    }

    protected String createADQLQuery(Map<String, String> params) throws ParseException, UnsupportedEncodingException {

        List<String> whereConditions = new ArrayList<>();
        for (Map.Entry<String,String> entry: params.entrySet()) {
            String key   = entry.getKey();
            String value = entry.getValue();
            if (key.equals(POS)) {
                String size = params.getOrDefault(SIZE, null);
                whereConditions.add(QueryCreator.createPosQuery(paramsToColumns.get(POS).toString(), value, size));
            } else if (key.equals(TIME)) {
                whereConditions.add(QueryCreator.createTimeQuery((List) paramsToColumns.get(TIME), value));
            }
        }

        StringBuffer adqlQuery = new StringBuffer();
        adqlQuery.append("SELECT ");
        if (params.containsKey(TOP))
            adqlQuery.append(" TOP ").append(params.get(TOP)).append(" ");
        adqlQuery.append(selectedColumns)
                 .append(" FROM ")
                 .append(tapTable);
        if (whereConditions.size() > 0) {
            adqlQuery.append(" WHERE ");
            adqlQuery.append(StringUtils.join(whereConditions, " AND "));
        }

        return URLEncoder.encode(adqlQuery.toString(), "ISO-8859-1");
    }

    private StringBuffer getAdqlURL() {
        StringBuffer buf = new StringBuffer(tapURL);

        buf.append("/sync?LANG=ADQL")
           .append("&FORMAT=votable%2Ftd")
           .append("&REQUEST=doQuery")
           .append("&QUERY=");

        return buf;
    }

}
