package com.example.backend.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Hidden
@Slf4j
public class ScalarDocsController {

    @GetMapping(value = {"/scalar", "/docs"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String getScalarDocs() {
        log.debug("[SCALAR_DOCS_CONTROLLER]: DEBUG - Rendering Scalar API reference page");
        return """
                <!doctype html>
                <html lang="en">
                  <head>
                    <title>SIMRS DAC - Scalar API Reference</title>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <link rel="icon" type="image/svg+xml" href="https://scalar.com/favicon.svg" />
                    <style>
                      body {
                        margin: 0;
                        padding: 0;
                      }
                    </style>
                  </head>
                  <body>
                    <script
                      id="api-reference"
                      data-url="/v3/api-docs"
                      data-configuration='{
                        "theme": "purple",
                        "layout": "modern",
                        "showSidebar": true,
                        "searchHotKey": "k",
                        "darkMode": true
                      }'>
                    </script>
                    <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
                  </body>
                </html>
                """;
    }
}
