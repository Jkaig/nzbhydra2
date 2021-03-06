package org.nzbhydra.okhttp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Stopwatch;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.nzbhydra.Jackson;
import org.nzbhydra.logging.LoggingMarkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings("ConstantConditions")
@Component
public class WebAccess {

    private static final Logger logger = LoggerFactory.getLogger(WebAccess.class);

    @Autowired
    private HydraOkHttp3ClientHttpRequestFactory requestFactory;
    @Value("${nzbhydra.connectionTimeout:10}")
    private int timeout;

    public String callUrl(String url) throws IOException {
        return callUrl(url, new HashMap<>());
    }

    public String callUrl(String url, Map<String, String> headers) throws IOException {
        return callUrl(url, headers, timeout);
    }

    public String callUrl(String url, Map<String, String> headers, int timeout) throws IOException {
        Builder builder = new Builder().url(url);
        for (Entry<String, String> entry : headers.entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }

        Request request = builder.build();

        OkHttpClient client = requestFactory.getOkHttpClientBuilder(request.url().uri()).readTimeout(timeout, TimeUnit.SECONDS).connectTimeout(timeout, TimeUnit.SECONDS).writeTimeout(timeout, TimeUnit.SECONDS).build();
        logger.debug(LoggingMarkers.HTTP, "Calling URL {} with headers {} and timeout {}", url, headers.entrySet().stream().map(x -> x.getKey() + ":" + x.getValue()).collect(Collectors.joining(", ")), timeout);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String error = String.format("URL call to %s returned %d: %s", url, response.code(), response.message());
                logger.error(error);
                throw new IOException(error);
            }
            String body = response.body().string();
            logger.debug(LoggingMarkers.HTTP, "Call to {} successful with content length {} and headers {}", url, body.length(), response.headers());
            response.body().close();
            return body;
        }
    }

    public <T> T callUrl(String url, Map<String, String> headers, Class<T> clazz) throws IOException {
        String body = callUrl(url, headers);
        return Jackson.JSON_MAPPER.readValue(body, clazz);
    }

    public <T> T callUrl(String url, TypeReference valueTypeRef) throws IOException {
        String body = callUrl(url);
        return Jackson.JSON_MAPPER.readValue(body, valueTypeRef);
    }

    public void downloadToFile(String url, File file) throws IOException {
        logger.debug("Downloading file from {} to {}", url, file.getAbsolutePath());
        Stopwatch stopwatch = Stopwatch.createStarted();
        Request request = new Request.Builder().url(url).build();
        try (Response response = requestFactory.getOkHttpClientBuilder(request.url().uri()).build().newCall(request).execute()) {
            long contentLength = response.body().contentLength();
            if (!response.isSuccessful()) {
                String error = String.format("URL call to %s returned %d:%s", url, response.code(), response.message());
                logger.error(error);
                throw new IOException(error);
            }
            BufferedSink sink = Okio.buffer(Okio.sink(file));
            sink.writeAll(response.body().source());
            sink.flush();
            sink.close();
            response.body().close();
            logger.debug(LoggingMarkers.PERFORMANCE, "Took {}ms to download file with {} bytes", stopwatch.elapsed(TimeUnit.MILLISECONDS), contentLength);
        }
    }

}
