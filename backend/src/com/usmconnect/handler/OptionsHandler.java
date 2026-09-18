package com.usmconnect.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.usmconnect.config.OptionsConfig;
import com.usmconnect.util.HttpUtil;

import java.io.IOException;

public final class OptionsHandler implements HttpHandler {

    private final OptionsConfig options;

    public OptionsHandler(OptionsConfig options) {
        this.options = options;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            HttpUtil.sendJson(exchange, 404, HttpUtil.error("Not found"));
            return;
        }
        HttpUtil.sendJson(exchange, 200, options.asMap());
    }
}
