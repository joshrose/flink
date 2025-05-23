/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.rest;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.runtime.io.network.netty.OutboundChannelHandlerFactory;
import org.apache.flink.runtime.io.network.netty.SSLHandlerFactory;
import org.apache.flink.runtime.rest.messages.EmptyMessageParameters;
import org.apache.flink.runtime.rest.messages.EmptyRequestBody;
import org.apache.flink.runtime.rest.messages.ErrorResponseBody;
import org.apache.flink.runtime.rest.messages.MessageHeaders;
import org.apache.flink.runtime.rest.messages.MessageParameters;
import org.apache.flink.runtime.rest.messages.RequestBody;
import org.apache.flink.runtime.rest.messages.ResponseBody;
import org.apache.flink.runtime.rest.util.RestClientException;
import org.apache.flink.runtime.rest.util.RestConstants;
import org.apache.flink.runtime.rest.util.RestMapperUtils;
import org.apache.flink.runtime.rest.versioning.RestAPIVersion;
import org.apache.flink.util.AutoCloseableAsync;
import org.apache.flink.util.ConfigurationException;
import org.apache.flink.util.ExceptionUtils;
import org.apache.flink.util.NetUtils;
import org.apache.flink.util.Preconditions;
import org.apache.flink.util.concurrent.ExecutorThreadFactory;
import org.apache.flink.util.concurrent.FutureUtils;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonParser;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JavaType;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.netty4.io.netty.bootstrap.Bootstrap;
import org.apache.flink.shaded.netty4.io.netty.buffer.ByteBuf;
import org.apache.flink.shaded.netty4.io.netty.buffer.ByteBufInputStream;
import org.apache.flink.shaded.netty4.io.netty.buffer.Unpooled;
import org.apache.flink.shaded.netty4.io.netty.channel.Channel;
import org.apache.flink.shaded.netty4.io.netty.channel.ChannelFuture;
import org.apache.flink.shaded.netty4.io.netty.channel.ChannelHandler;
import org.apache.flink.shaded.netty4.io.netty.channel.ChannelHandlerContext;
import org.apache.flink.shaded.netty4.io.netty.channel.ChannelInitializer;
import org.apache.flink.shaded.netty4.io.netty.channel.ChannelOption;
import org.apache.flink.shaded.netty4.io.netty.channel.DefaultSelectStrategyFactory;
import org.apache.flink.shaded.netty4.io.netty.channel.EventLoopGroup;
import org.apache.flink.shaded.netty4.io.netty.channel.SelectStrategyFactory;
import org.apache.flink.shaded.netty4.io.netty.channel.SimpleChannelInboundHandler;
import org.apache.flink.shaded.netty4.io.netty.channel.nio.NioEventLoopGroup;
import org.apache.flink.shaded.netty4.io.netty.channel.socket.SocketChannel;
import org.apache.flink.shaded.netty4.io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.TooLongFrameException;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.DefaultFullHttpRequest;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.FullHttpResponse;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpClientCodec;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpHeaderNames;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpHeaderValues;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpHeaders;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpMethod;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpObjectAggregator;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpRequest;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpResponse;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpVersion;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.multipart.Attribute;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.multipart.MemoryAttribute;
import org.apache.flink.shaded.netty4.io.netty.handler.ssl.SslHandler;
import org.apache.flink.shaded.netty4.io.netty.handler.stream.ChunkedWriteHandler;
import org.apache.flink.shaded.netty4.io.netty.handler.timeout.IdleStateEvent;
import org.apache.flink.shaded.netty4.io.netty.handler.timeout.IdleStateHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.channels.spi.SelectorProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.apache.flink.configuration.SecurityOptions.SSL_REST_ENABLED;
import static org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;

/** This client is the counter-part to the {@link RestServerEndpoint}. */
public class RestClient implements AutoCloseableAsync {
    private static final Logger LOG = LoggerFactory.getLogger(RestClient.class);

    private static final ObjectMapper objectMapper = RestMapperUtils.getStrictObjectMapper();
    private static final ObjectMapper flexibleObjectMapper =
            RestMapperUtils.getFlexibleObjectMapper();

    // used to open connections to a rest server endpoint
    private final Executor executor;

    private final Bootstrap bootstrap;

    private final CompletableFuture<Void> terminationFuture;

    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    public static final String VERSION_PLACEHOLDER = "{{VERSION}}";

    private final String urlPrefix;

    // Used to track unresolved request futures in case they need to be resolved when the client is
    // closed
    private final Collection<CompletableFuture<Channel>> responseChannelFutures =
            ConcurrentHashMap.newKeySet();

    private final List<OutboundChannelHandlerFactory> outboundChannelHandlerFactories;
    private final boolean useInternalEventLoopGroup;

    /**
     * Creates a new RestClient for the provided root URL. If the protocol of the URL is "https",
     * then SSL is automatically enabled for the REST client.
     */
    public static RestClient forUrl(Configuration configuration, Executor executor, URL rootUrl)
            throws ConfigurationException {
        return forUrl(configuration, executor, rootUrl, null);
    }

    public static RestClient forUrl(
            Configuration configuration, Executor executor, URL rootUrl, EventLoopGroup group)
            throws ConfigurationException {
        Preconditions.checkNotNull(configuration);
        Preconditions.checkNotNull(rootUrl);
        if ("https".equals(rootUrl.getProtocol())) {
            configuration = configuration.clone();
            configuration.set(SSL_REST_ENABLED, true);
        }
        return new RestClient(configuration, executor, rootUrl.getHost(), rootUrl.getPort(), group);
    }

    public RestClient(Configuration configuration, Executor executor)
            throws ConfigurationException {
        this(configuration, executor, null, -1, null);
    }

    public RestClient(
            Configuration configuration,
            Executor executor,
            String host,
            int port,
            EventLoopGroup group)
            throws ConfigurationException {
        this(configuration, executor, host, port, DefaultSelectStrategyFactory.INSTANCE, group);
    }

    @VisibleForTesting
    RestClient(
            Configuration configuration,
            Executor executor,
            SelectStrategyFactory selectStrategyFactory)
            throws ConfigurationException {
        this(configuration, executor, null, -1, selectStrategyFactory, null);
    }

    private RestClient(
            Configuration configuration,
            Executor executor,
            String host,
            int port,
            SelectStrategyFactory selectStrategyFactory,
            @Nullable EventLoopGroup group)
            throws ConfigurationException {
        Preconditions.checkNotNull(configuration);
        this.executor = Preconditions.checkNotNull(executor);
        this.terminationFuture = new CompletableFuture<>();
        outboundChannelHandlerFactories = new ArrayList<>();
        ServiceLoader<OutboundChannelHandlerFactory> loader =
                ServiceLoader.load(OutboundChannelHandlerFactory.class);
        final Iterator<OutboundChannelHandlerFactory> factories = loader.iterator();
        while (factories.hasNext()) {
            try {
                final OutboundChannelHandlerFactory factory = factories.next();
                if (factory != null) {
                    outboundChannelHandlerFactories.add(factory);
                    LOG.info("Loaded channel outbound factory: {}", factory);
                }
            } catch (Throwable e) {
                LOG.error("Could not load channel outbound factory.", e);
                throw e;
            }
        }
        outboundChannelHandlerFactories.sort(
                Comparator.comparingInt(OutboundChannelHandlerFactory::priority).reversed());

        urlPrefix = configuration.get(RestOptions.URL_PREFIX);
        Preconditions.checkArgument(
                urlPrefix.startsWith("/") && urlPrefix.endsWith("/"),
                "urlPrefix must start and end with '/'");

        final RestClientConfiguration restConfiguration =
                RestClientConfiguration.fromConfiguration(configuration);
        final SSLHandlerFactory sslHandlerFactory = restConfiguration.getSslHandlerFactory();
        ChannelInitializer<SocketChannel> initializer =
                new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        try {
                            // SSL should be the first handler in the pipeline
                            if (sslHandlerFactory != null) {
                                SslHandler nettySSLHandler =
                                        host == null
                                                ? sslHandlerFactory.createNettySSLHandler(
                                                        socketChannel.alloc())
                                                : sslHandlerFactory.createNettySSLHandler(
                                                        socketChannel.alloc(), host, port);
                                socketChannel.pipeline().addLast("ssl", nettySSLHandler);
                            }
                            socketChannel
                                    .pipeline()
                                    .addLast(new HttpClientCodec())
                                    .addLast(
                                            new HttpObjectAggregator(
                                                    restConfiguration.getMaxContentLength()));

                            for (OutboundChannelHandlerFactory factory :
                                    outboundChannelHandlerFactories) {
                                Optional<ChannelHandler> channelHandler =
                                        factory.createHandler(configuration);
                                if (channelHandler.isPresent()) {
                                    socketChannel.pipeline().addLast(channelHandler.get());
                                }
                            }

                            socketChannel
                                    .pipeline()
                                    .addLast(new ChunkedWriteHandler()) // required for
                                    // multipart-requests
                                    .addLast(
                                            new IdleStateHandler(
                                                    restConfiguration.getIdlenessTimeout(),
                                                    restConfiguration.getIdlenessTimeout(),
                                                    restConfiguration.getIdlenessTimeout(),
                                                    TimeUnit.MILLISECONDS))
                                    .addLast(new ClientHandler());
                        } catch (Throwable t) {
                            t.printStackTrace();
                            ExceptionUtils.rethrow(t);
                        }
                    }
                };

        if (group == null) {
            // No NioEventLoopGroup constructor available that allows passing nThreads,
            // threadFactory,
            // and selectStrategyFactory without also passing a SelectorProvider, so mimicking its
            // default value seen in other constructors
            group =
                    new NioEventLoopGroup(
                            1,
                            new ExecutorThreadFactory("flink-rest-client-netty"),
                            SelectorProvider.provider(),
                            selectStrategyFactory);
            useInternalEventLoopGroup = true;
        } else {
            Preconditions.checkArgument(
                    !group.isShuttingDown() && !group.isShutdown(),
                    "provided eventLoopGroup is shut/shutting down");
            useInternalEventLoopGroup = false;
        }

        bootstrap = new Bootstrap();
        bootstrap
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(restConfiguration.getConnectionTimeout()))
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(initializer);

        LOG.debug("Rest client endpoint started.");
    }

    @VisibleForTesting
    Collection<CompletableFuture<Channel>> getResponseChannelFutures() {
        return responseChannelFutures;
    }

    @VisibleForTesting
    List<OutboundChannelHandlerFactory> getOutboundChannelHandlerFactories() {
        return outboundChannelHandlerFactories;
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        return shutdownInternally(Duration.ofSeconds(10L));
    }

    public void shutdown(Duration timeout) {
        final CompletableFuture<Void> shutDownFuture = shutdownInternally(timeout);

        try {
            shutDownFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            LOG.debug("Rest endpoint shutdown complete.");
        } catch (Exception e) {
            LOG.warn("Rest endpoint shutdown failed.", e);
        }
    }

    private CompletableFuture<Void> shutdownInternally(Duration timeout) {
        if (isRunning.compareAndSet(true, false)) {
            LOG.debug("Shutting down rest endpoint.");

            if (bootstrap != null) {
                if (bootstrap.config().group() != null && useInternalEventLoopGroup) {
                    bootstrap
                            .config()
                            .group()
                            .shutdownGracefully(0L, timeout.toMillis(), TimeUnit.MILLISECONDS)
                            .addListener(
                                    finished -> {
                                        notifyResponseFuturesOfShutdown();

                                        if (finished.isSuccess()) {
                                            terminationFuture.complete(null);
                                        } else {
                                            terminationFuture.completeExceptionally(
                                                    finished.cause());
                                        }
                                    });
                }
            }
        }
        return terminationFuture;
    }

    private void notifyResponseFuturesOfShutdown() {
        responseChannelFutures.forEach(
                future ->
                        future.completeExceptionally(
                                new IllegalStateException(
                                        "RestClient closed before request completed")));
        responseChannelFutures.clear();
    }

    public <
                    M extends MessageHeaders<EmptyRequestBody, P, EmptyMessageParameters>,
                    P extends ResponseBody>
            CompletableFuture<P> sendRequest(String targetAddress, int targetPort, M messageHeaders)
                    throws IOException {
        return sendRequest(
                targetAddress,
                targetPort,
                messageHeaders,
                EmptyMessageParameters.getInstance(),
                EmptyRequestBody.getInstance());
    }

    public <
                    M extends MessageHeaders<R, P, U>,
                    U extends MessageParameters,
                    R extends RequestBody,
                    P extends ResponseBody>
            CompletableFuture<P> sendRequest(
                    String targetAddress,
                    int targetPort,
                    M messageHeaders,
                    U messageParameters,
                    R request)
                    throws IOException {
        return sendRequest(
                targetAddress,
                targetPort,
                messageHeaders,
                messageParameters,
                request,
                Collections.emptyList());
    }

    public <
                    M extends MessageHeaders<R, P, U>,
                    U extends MessageParameters,
                    R extends RequestBody,
                    P extends ResponseBody>
            CompletableFuture<P> sendRequest(
                    String targetAddress,
                    int targetPort,
                    M messageHeaders,
                    U messageParameters,
                    R request,
                    Collection<FileUpload> fileUploads)
                    throws IOException {
        Collection<? extends RestAPIVersion> supportedAPIVersions =
                messageHeaders.getSupportedAPIVersions();
        return sendRequest(
                targetAddress,
                targetPort,
                messageHeaders,
                messageParameters,
                request,
                fileUploads,
                RestAPIVersion.getLatestVersion(supportedAPIVersions));
    }

    public <
                    M extends MessageHeaders<R, P, U>,
                    U extends MessageParameters,
                    R extends RequestBody,
                    P extends ResponseBody>
            CompletableFuture<P> sendRequest(
                    String targetAddress,
                    int targetPort,
                    M messageHeaders,
                    U messageParameters,
                    R request,
                    Collection<FileUpload> fileUploads,
                    RestAPIVersion<? extends RestAPIVersion<?>> apiVersion)
                    throws IOException {
        Preconditions.checkNotNull(targetAddress);
        Preconditions.checkArgument(
                NetUtils.isValidHostPort(targetPort),
                "The target port " + targetPort + " is not in the range [0, 65535].");
        Preconditions.checkNotNull(messageHeaders);
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(messageParameters);
        Preconditions.checkNotNull(fileUploads);
        Preconditions.checkState(
                messageParameters.isResolved(), "Message parameters were not resolved.");

        if (!messageHeaders.getSupportedAPIVersions().contains(apiVersion)) {
            throw new IllegalArgumentException(
                    String.format(
                            "The requested version %s is not supported by the request (method=%s URL=%s). Supported versions are: %s.",
                            apiVersion,
                            messageHeaders.getHttpMethod(),
                            messageHeaders.getTargetRestEndpointURL(),
                            messageHeaders.getSupportedAPIVersions().stream()
                                    .map(RestAPIVersion::getURLVersionPrefix)
                                    .collect(Collectors.joining(","))));
        }

        String versionedHandlerURL =
                constructVersionedHandlerUrl(
                        messageHeaders, apiVersion.getURLVersionPrefix(), this.urlPrefix);
        String targetUrl = MessageParameters.resolveUrl(versionedHandlerURL, messageParameters);

        LOG.debug(
                "Sending request of class {} to {}:{}{}",
                request.getClass(),
                targetAddress,
                targetPort,
                targetUrl);
        // serialize payload
        StringWriter sw = new StringWriter();
        objectMapper.writeValue(sw, request);
        ByteBuf payload =
                Unpooled.wrappedBuffer(sw.toString().getBytes(ConfigConstants.DEFAULT_CHARSET));

        Request httpRequest =
                createRequest(
                        targetAddress + ':' + targetPort,
                        targetUrl,
                        messageHeaders.getHttpMethod().getNettyHttpMethod(),
                        payload,
                        fileUploads,
                        messageHeaders.getCustomHeaders());

        final JavaType responseType;

        final Collection<Class<?>> typeParameters = messageHeaders.getResponseTypeParameters();

        if (typeParameters.isEmpty()) {
            responseType = objectMapper.constructType(messageHeaders.getResponseClass());
        } else {
            responseType =
                    objectMapper
                            .getTypeFactory()
                            .constructParametricType(
                                    messageHeaders.getResponseClass(),
                                    typeParameters.toArray(new Class<?>[typeParameters.size()]));
        }

        return submitRequest(targetAddress, targetPort, httpRequest, responseType);
    }

    private static <M extends MessageHeaders<?, ?, ?>> String constructVersionedHandlerUrl(
            M messageHeaders, String urlVersionPrefix, String urlPrefix) {
        String targetUrl = messageHeaders.getTargetRestEndpointURL();
        if (targetUrl.contains(VERSION_PLACEHOLDER)) {
            return targetUrl.replace(VERSION_PLACEHOLDER, urlVersionPrefix);
        } else {
            return urlPrefix + urlVersionPrefix + messageHeaders.getTargetRestEndpointURL();
        }
    }

    private static Request createRequest(
            String targetAddress,
            String targetUrl,
            HttpMethod httpMethod,
            ByteBuf jsonPayload,
            Collection<FileUpload> fileUploads,
            Collection<HttpHeader> customHeaders)
            throws IOException {
        if (fileUploads.isEmpty()) {

            HttpRequest httpRequest =
                    new DefaultFullHttpRequest(
                            HttpVersion.HTTP_1_1, httpMethod, targetUrl, jsonPayload);

            HttpHeaders headers = httpRequest.headers();
            headers.set(HttpHeaderNames.HOST, targetAddress)
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                    .add(HttpHeaderNames.CONTENT_LENGTH, jsonPayload.capacity())
                    .add(HttpHeaderNames.CONTENT_TYPE, RestConstants.REST_CONTENT_TYPE);
            customHeaders.forEach(ch -> headers.set(ch.getName(), ch.getValue()));

            return new SimpleRequest(httpRequest);
        } else {
            HttpRequest httpRequest =
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, httpMethod, targetUrl);

            HttpHeaders headers = httpRequest.headers();
            headers.set(HttpHeaderNames.HOST, targetAddress)
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            customHeaders.forEach(ch -> headers.set(ch.getName(), ch.getValue()));

            // takes care of splitting the request into multiple parts
            HttpPostRequestEncoder bodyRequestEncoder;
            try {
                // we could use mixed attributes here but we have to ensure that the minimum size is
                // greater than
                // any file as the upload otherwise fails
                DefaultHttpDataFactory httpDataFactory = new DefaultHttpDataFactory(true);
                // the FileUploadHandler explicitly checks for multipart headers
                bodyRequestEncoder = new HttpPostRequestEncoder(httpDataFactory, httpRequest, true);

                Attribute requestAttribute =
                        new MemoryAttribute(FileUploadHandler.HTTP_ATTRIBUTE_REQUEST);
                requestAttribute.setContent(jsonPayload);
                bodyRequestEncoder.addBodyHttpData(requestAttribute);

                int fileIndex = 0;
                for (FileUpload fileUpload : fileUploads) {
                    Path path = fileUpload.getFile();
                    if (Files.isDirectory(path)) {
                        throw new IllegalArgumentException(
                                "Upload of directories is not supported. Dir=" + path);
                    }
                    File file = path.toFile();
                    LOG.trace("Adding file {} to request.", file);
                    bodyRequestEncoder.addBodyFileUpload(
                            "file_" + fileIndex, file, fileUpload.getContentType(), false);
                    fileIndex++;
                }
            } catch (HttpPostRequestEncoder.ErrorDataEncoderException e) {
                throw new IOException("Could not encode request.", e);
            }

            try {
                httpRequest = bodyRequestEncoder.finalizeRequest();
            } catch (HttpPostRequestEncoder.ErrorDataEncoderException e) {
                throw new IOException("Could not finalize request.", e);
            }

            return new MultipartRequest(httpRequest, bodyRequestEncoder);
        }
    }

    private <P extends ResponseBody> CompletableFuture<P> submitRequest(
            String targetAddress, int targetPort, Request httpRequest, JavaType responseType) {
        if (!isRunning.get()) {
            return FutureUtils.completedExceptionally(
                    new IllegalStateException("RestClient is already closed"));
        }

        final CompletableFuture<Channel> channelFuture = new CompletableFuture<>();
        responseChannelFutures.add(channelFuture);

        final ChannelFuture connectFuture = bootstrap.connect(targetAddress, targetPort);
        connectFuture.addListener(
                (ChannelFuture future) -> {
                    responseChannelFutures.remove(channelFuture);

                    if (future.isSuccess()) {
                        channelFuture.complete(future.channel());
                    } else {
                        channelFuture.completeExceptionally(future.cause());
                    }
                });

        return channelFuture
                .thenComposeAsync(
                        channel -> {
                            ClientHandler handler = channel.pipeline().get(ClientHandler.class);

                            CompletableFuture<JsonResponse> future;
                            boolean success = false;

                            try {
                                if (handler == null) {
                                    throw new IOException(
                                            "Netty pipeline was not properly initialized.");
                                } else {
                                    httpRequest.writeTo(channel);
                                    future = handler.getJsonFuture();
                                    success = true;
                                }
                            } catch (IOException e) {
                                future =
                                        FutureUtils.completedExceptionally(
                                                new ConnectionException(
                                                        "Could not write request.", e));
                            } finally {
                                if (!success) {
                                    channel.close();
                                }
                            }

                            return future;
                        },
                        executor)
                .thenComposeAsync(
                        (JsonResponse rawResponse) -> parseResponse(rawResponse, responseType),
                        executor);
    }

    private static <P extends ResponseBody> CompletableFuture<P> parseResponse(
            JsonResponse rawResponse, JavaType responseType) {
        CompletableFuture<P> responseFuture = new CompletableFuture<>();
        final JsonParser jsonParser = objectMapper.treeAsTokens(rawResponse.json);
        try {
            // We make sure it fits to ErrorResponseBody, this condition is enforced by test in
            // RestClientTest
            if (rawResponse.json.size() == 1 && rawResponse.json.has("errors")) {
                ErrorResponseBody error =
                        objectMapper.treeToValue(rawResponse.getJson(), ErrorResponseBody.class);
                responseFuture.completeExceptionally(
                        new RestClientException(
                                error.errors.toString(), rawResponse.getHttpResponseStatus()));
            } else {
                P response = flexibleObjectMapper.readValue(jsonParser, responseType);
                responseFuture.complete(response);
            }
        } catch (IOException ex) {
            // if this fails it is either the expected type or response type was wrong, most
            // likely caused
            // by a client/search MessageHeaders mismatch
            LOG.error(
                    "Received response was neither of the expected type ({}) nor an error. Response={}",
                    responseType,
                    rawResponse,
                    ex);
            responseFuture.completeExceptionally(
                    new RestClientException(
                            "Response was neither of the expected type("
                                    + responseType
                                    + ") nor an error.",
                            ex,
                            rawResponse.getHttpResponseStatus()));
        }
        return responseFuture;
    }

    private interface Request {
        void writeTo(Channel channel) throws IOException;
    }

    private static final class SimpleRequest implements Request {
        private final HttpRequest httpRequest;

        SimpleRequest(HttpRequest httpRequest) {
            this.httpRequest = httpRequest;
        }

        @Override
        public void writeTo(Channel channel) {
            channel.writeAndFlush(httpRequest);
        }
    }

    private static final class MultipartRequest implements Request {
        private final HttpRequest httpRequest;
        private final HttpPostRequestEncoder bodyRequestEncoder;

        MultipartRequest(HttpRequest httpRequest, HttpPostRequestEncoder bodyRequestEncoder) {
            this.httpRequest = httpRequest;
            this.bodyRequestEncoder = bodyRequestEncoder;
        }

        @Override
        public void writeTo(Channel channel) {
            ChannelFuture future = channel.writeAndFlush(httpRequest);
            // this should never be false as we explicitly set the encoder to use multipart messages
            if (bodyRequestEncoder.isChunked()) {
                future = channel.writeAndFlush(bodyRequestEncoder);
            }

            // release data and remove temporary files if they were created, once the writing is
            // complete
            future.addListener((ignored) -> bodyRequestEncoder.cleanFiles());
        }
    }

    private static class ClientHandler extends SimpleChannelInboundHandler<Object> {

        private final CompletableFuture<JsonResponse> jsonFuture = new CompletableFuture<>();

        CompletableFuture<JsonResponse> getJsonFuture() {
            return jsonFuture;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof HttpResponse
                    && ((HttpResponse) msg).status().equals(REQUEST_ENTITY_TOO_LARGE)) {
                jsonFuture.completeExceptionally(
                        new RestClientException(
                                String.format(
                                        REQUEST_ENTITY_TOO_LARGE + ". Try to raise [%s]",
                                        RestOptions.CLIENT_MAX_CONTENT_LENGTH.key()),
                                ((HttpResponse) msg).status()));
            } else if (msg instanceof FullHttpResponse) {
                readRawResponse((FullHttpResponse) msg);
            } else {
                LOG.error(
                        "Implementation error: Received a response that wasn't a FullHttpResponse.");
                if (msg instanceof HttpResponse) {
                    jsonFuture.completeExceptionally(
                            new RestClientException(
                                    "Implementation error: Received a response that wasn't a FullHttpResponse.",
                                    ((HttpResponse) msg).status()));
                } else {
                    jsonFuture.completeExceptionally(
                            new RestClientException(
                                    "Implementation error: Received a response that wasn't a FullHttpResponse.",
                                    HttpResponseStatus.INTERNAL_SERVER_ERROR));
                }
            }
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            jsonFuture.completeExceptionally(
                    new ConnectionClosedException("Channel became inactive."));
            ctx.close();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                jsonFuture.completeExceptionally(
                        new ConnectionIdleException("Channel became idle."));
                ctx.close();
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
            if (cause instanceof TooLongFrameException) {
                jsonFuture.completeExceptionally(
                        new TooLongFrameException(
                                String.format(
                                        cause.getMessage() + " Try to raise [%s]",
                                        RestOptions.CLIENT_MAX_CONTENT_LENGTH.key())));
            } else {
                jsonFuture.completeExceptionally(cause);
            }
            ctx.close();
        }

        private void readRawResponse(FullHttpResponse msg) {
            ByteBuf content = msg.content();

            JsonNode rawResponse;
            try (InputStream in = new ByteBufInputStream(content)) {
                rawResponse = objectMapper.readTree(in);
                LOG.debug("Received response {}.", rawResponse);
            } catch (JsonProcessingException je) {
                LOG.error("Response was not valid JSON.", je);
                // let's see if it was a plain-text message instead
                content.readerIndex(0);
                try (ByteBufInputStream in = new ByteBufInputStream(content)) {
                    byte[] data = new byte[in.available()];
                    in.readFully(data);
                    String message = new String(data);
                    LOG.error("Unexpected plain-text response: {}", message);
                    jsonFuture.completeExceptionally(
                            new RestClientException(
                                    "Response was not valid JSON, but plain-text: " + message,
                                    je,
                                    msg.status()));
                } catch (IOException e) {
                    jsonFuture.completeExceptionally(
                            new RestClientException(
                                    "Response was not valid JSON, nor plain-text.",
                                    je,
                                    msg.status()));
                }
                return;
            } catch (IOException ioe) {
                LOG.error("Response could not be read.", ioe);
                jsonFuture.completeExceptionally(
                        new RestClientException("Response could not be read.", ioe, msg.status()));
                return;
            }
            jsonFuture.complete(new JsonResponse(rawResponse, msg.status()));
        }
    }

    private static final class JsonResponse {
        private final JsonNode json;
        private final HttpResponseStatus httpResponseStatus;

        private JsonResponse(JsonNode json, HttpResponseStatus httpResponseStatus) {
            this.json = Preconditions.checkNotNull(json);
            this.httpResponseStatus = Preconditions.checkNotNull(httpResponseStatus);
        }

        public JsonNode getJson() {
            return json;
        }

        public HttpResponseStatus getHttpResponseStatus() {
            return httpResponseStatus;
        }

        @Override
        public String toString() {
            return "JsonResponse{"
                    + "json="
                    + json
                    + ", httpResponseStatus="
                    + httpResponseStatus
                    + '}';
        }
    }
}
