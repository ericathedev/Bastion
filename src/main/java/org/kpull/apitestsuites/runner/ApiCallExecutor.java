package org.kpull.apitestsuites.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequest;
import com.mashape.unirest.request.HttpRequestWithBody;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.apache.commons.lang.StringUtils;
import org.kpull.apitestsuites.core.*;

import java.io.IOException;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:mail@kylepullicino.com">Kyle</a>
 */
public class ApiCallExecutor {

    private ApiEnvironment environment;
    private ApiCall apiCallToExecute;
    private ObjectMapper objectMapper;

    public ApiCallExecutor(ApiEnvironment environment, ApiCall apiCallToExecute, ObjectMapper objectMapper) {
        Objects.requireNonNull(environment);
        Objects.requireNonNull(apiCallToExecute);
        this.environment = environment;
        this.apiCallToExecute = apiCallToExecute;
        this.objectMapper = objectMapper;
    }

    public ApiEnvironment getEnvironment() {
        return environment;
    }

    public ApiCall getApiCallToExecute() {
        return apiCallToExecute;
    }

    public void execute() {
        try {
            ApiRequest request = apiCallToExecute.getRequest();
            HttpRequest httpRequest = createHttpRequest(request);
            if (httpRequest instanceof HttpRequestWithBody) {
                ((HttpRequestWithBody) httpRequest).body(environment.process(request.getBody()));
            }
            request.getHeaders().forEach(header -> {
                httpRequest.header(header.getName(), environment.process(header.getValue()));
            });
            request.getQueryParams().forEach(queryParam -> {
                httpRequest.queryString(queryParam.getName(), environment.process(queryParam.getValue()));
            });
            HttpResponse<JsonNode> httpResponse = httpRequest.asJson();
            // TODO: Refine the next statement
            ApiResponse response = new ApiResponse(httpResponse.getHeaders().entrySet().stream().flatMap(header -> header.getValue().stream().map(value -> new ApiHeader(header.getKey(), value))).collect(Collectors.toList()),
                    httpResponse.getStatus(), "application/json", httpResponse.getBody().toString());
            apiCallToExecute.setResponse(response);
            if (!Strings.isNullOrEmpty(apiCallToExecute.getPostCallScript())) {
                Binding binding = new Binding();
                binding.setVariable("apiRequest", request);
                binding.setVariable("httpRequest", httpRequest);
                binding.setVariable("apiResponse", response);
                binding.setVariable("httpResponse", httpResponse);
                binding.setVariable("environment", environment);
                apiCallToExecute.getResponseModel().ifPresent((modelClass) -> {
                    if (StringUtils.defaultString(httpResponse.getHeaders().getFirst("content-type")).startsWith("application/json")) {
                        try {
                            Objects.requireNonNull(objectMapper, "Object Mapper must be set before we can deserialize to a model object");
                            Object deserializedModel = objectMapper.reader(modelClass).readValue(httpResponse.getRawBody());
                            binding.setVariable("model", deserializedModel);
                        } catch (IOException e) {
                            throw new IllegalStateException("Could not deserialize JSON", e);
                        }
                    }
                });
                GroovyShell groovy = new GroovyShell(binding);
                groovy.evaluate(apiCallToExecute.getPostCallScript());
            }
        } catch (UnirestException e) {
            e.printStackTrace();
        }
    }

    private HttpRequest createHttpRequest(ApiRequest request) {
        HttpRequest httpRequest = null;
        switch (request.getMethod()) {
            case "POST":
                httpRequest = Unirest.post(environment.process(request.getUrl()));
                break;
            case "GET":
                httpRequest = Unirest.get(environment.process(request.getUrl()));
                break;
            case "PUT":
                httpRequest = Unirest.put(environment.process(request.getUrl()));
                break;
            case "DELETE":
                httpRequest = Unirest.delete(environment.process(request.getUrl()));
                break;
            case "OPTIONS":
                httpRequest = Unirest.options(environment.process(request.getUrl()));
                break;
            case "HEAD":
                httpRequest = Unirest.head(environment.process(request.getUrl()));
                break;
            case "PATCH":
                httpRequest = Unirest.patch(environment.process(request.getUrl()));
                break;
        }
        return httpRequest;
    }
}
