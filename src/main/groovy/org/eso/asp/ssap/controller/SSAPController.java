package org.eso.asp.ssap.controller;

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

import org.eso.asp.ssap.domain.ParameterMappings;
import org.eso.asp.ssap.service.SSAPService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller implementing the /ssa API of SSAP
 *
 * @author Vincenzo Forch&igrave (ESO), vforchi@eso.org, vincenzo.forchi@gmail.com
 */
@RestController
@Configurable
@RequestMapping("/ssa")
public class SSAPController {

    @Autowired
    SSAPService service;

    @Value("#{${ssap.versions.supported:{'1.1'}}}")
    List<String> supportedVersions;

    @Value("#{${ssap.formats.supported:{'all', 'compliant', 'native', 'fits', 'application/fits'}}}")
    List<String> supportedFormats;

    @RequestMapping(method = RequestMethod.GET, produces = { MediaType.TEXT_XML_VALUE })
    @ResponseBody
    ResponseEntity<?> getSpectra(
            @RequestParam(value = "VERSION", required = false) String version,
            @RequestParam(value = "REQUEST")                   String request,
            @RequestParam(value = "FORMAT", required = false)  String format,
            @RequestParam                                      Map<String, String> allParams) {

        try {
            if (version != null && !supportedVersions.contains(version))
                return ResponseEntity.badRequest().body("VERSION=" + version + " is not supported");

            if (format != null) {
                if (format.toLowerCase() == "metadata") {
                    return ResponseEntity.badRequest().body("FORMAT=" + format + " is not supported"); // TODO
                } else if (!supportedFormats.contains(format.toLowerCase())) {
                    return ResponseEntity.badRequest().body("FORMAT=" + format + " is not supported");
                }
            }

            if (request.equals(ParameterMappings.QUERY_DATA)) {
                Object body = service.queryData(allParams);
                return ResponseEntity.ok(body);
            } else
                return ResponseEntity.badRequest().body("REQUEST=" + request + " is not implemented");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
}
