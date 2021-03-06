package rocks.bastion.core;

import com.mashape.unirest.http.HttpClientHelper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequestWithBody;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Responsible for executing a Bastion remote request built using the {@link BastionBuilderImpl} builder and prepare a response object.
 */
public class RequestExecutor {

    private HttpRequest bastionHttpRequest;
    private com.mashape.unirest.request.HttpRequest executableHttpRequest;
    private Collection<ApiHeader> headers;
    private String resolvedUrl;

    public RequestExecutor(HttpRequest bastionHttpRequest) {
        Objects.requireNonNull(bastionHttpRequest);
        this.bastionHttpRequest = bastionHttpRequest;
        executableHttpRequest = identifyHttpRequest();
        applyHeaders();
        applyQueryParameters();
        applyRouteParameters();
        applyBody();
    }

    public String getMethod() {
        return bastionHttpRequest.method().getValue();
    }

    public String getResolvedUrl() {
        return resolvedUrl;
    }

    public Collection<ApiHeader> getHeaders() {
        return Collections.unmodifiableCollection(headers);
    }

    /**
     * Executes the given HTTP request and retrieves the response.
     *
     * @return The HTTP response retrieved from the remote server.
     */
    public Response execute() {
        try {
            HttpResponse<InputStream> httpResponse = performRequest();
            return convertToRawResponse(httpResponse);
        } catch (UnirestException exception) {
            throw new IllegalStateException("Failed executing request", exception);
        }
    }

    private com.mashape.unirest.request.HttpRequest identifyHttpRequest() {
        switch (bastionHttpRequest.method().getValue()) {
            case "GET":
                return Unirest.get(bastionHttpRequest.url());
            case "POST":
                return Unirest.post(bastionHttpRequest.url());
            case "PATCH":
                return Unirest.patch(bastionHttpRequest.url());
            case "DELETE":
                return Unirest.delete(bastionHttpRequest.url());
            case "PUT":
                return Unirest.put(bastionHttpRequest.url());
            case "OPTIONS":
                return Unirest.options(bastionHttpRequest.url());
            case "HEAD":
                return Unirest.head(bastionHttpRequest.url());
            default:
                throw new UnsupportedOperationException(String.format("We cannot perform a request of type %s.", bastionHttpRequest.method().getValue()));
        }
    }

    private void applyHeaders() {
        headers = new LinkedList<>();
        if (!bastionHttpRequest.headers().stream().anyMatch(header -> header.getName().equalsIgnoreCase("content-type")) && bastionHttpRequest.contentType().isPresent()) {
            executableHttpRequest.header("Content-Type", bastionHttpRequest.contentType().get().toString());
            headers.add(new ApiHeader("Content-Type", bastionHttpRequest.contentType().get().toString()));
        }
        bastionHttpRequest.headers().stream().forEach(header -> executableHttpRequest.header(header.getName(), header.getValue()));
        headers.addAll(bastionHttpRequest.headers());
    }

    private void applyQueryParameters() {
        bastionHttpRequest.queryParams().stream().forEach(queryParam -> executableHttpRequest.queryString(queryParam.getName(), queryParam.getValue()));
        resolvedUrl = executableHttpRequest.getUrl();
    }

    private void applyRouteParameters() {
        bastionHttpRequest.routeParams().stream().forEach(routeParam -> executableHttpRequest.routeParam(routeParam.getName(), routeParam.getValue()));
        resolvedUrl = executableHttpRequest.getUrl();
    }

    private void applyBody() {
        if (executableHttpRequest instanceof HttpRequestWithBody) {
            ((HttpRequestWithBody) executableHttpRequest).body(bastionHttpRequest.body().toString());
        }
    }

    private HttpResponse<InputStream> performRequest() throws UnirestException {
        return executableHttpRequest.asBinary();
    }

    private Response convertToRawResponse(HttpResponse<InputStream> httpResponse) {
        return new RawResponse(httpResponse.getStatus(),
                httpResponse.getStatusText(),
                httpResponse.getHeaders().entrySet().stream().flatMap(header ->
                        header.getValue().stream().map(headerValue ->
                                new ApiHeader(header.getKey(), headerValue))).collect(Collectors.toList()),
                httpResponse.getBody());
    }
}
