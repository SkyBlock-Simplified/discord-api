package dev.simplified.discordapi.harness.gateway;

import discord4j.common.close.CloseStatus;
import discord4j.discordjson.json.gateway.Dispatch;
import discord4j.gateway.GatewayClient;
import discord4j.gateway.GatewayConnection;
import discord4j.gateway.json.GatewayPayload;
import io.netty.buffer.ByteBuf;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * In-JVM fake {@link GatewayClient} that drives Discord4J's real dispatch pipeline without a socket.
 * <p>
 * On {@link #execute(String)} it replays a handshake sequence (a {@code READY} plus a
 * {@code GatewayStateChange.connected()}) into {@link #dispatch()}, which completes the framework's
 * login and emits {@code ConnectEvent}. Tests then push further dispatches via {@link #emit(Dispatch)}.
 */
public final class FakeGatewayClient implements GatewayClient {

    private final Sinks.Many<Dispatch> dispatchSink = Sinks.many().replay().all();
    private final Sinks.Many<GatewayPayload<?>> senderSink = Sinks.many().multicast().onBackpressureBuffer();
    private final List<Dispatch> handshake;
    private final int shardCount;

    public FakeGatewayClient(@org.jetbrains.annotations.NotNull List<Dispatch> handshake, int shardCount) {
        this.handshake = List.copyOf(handshake);
        this.shardCount = shardCount;
    }

    /**
     * Pushes a dispatch (e.g. an {@code INTERACTION_CREATE}) into the live event pipeline.
     *
     * @param dispatch the dispatch to publish
     */
    public synchronized void emit(@org.jetbrains.annotations.NotNull Dispatch dispatch) {
        this.dispatchSink.emitNext(dispatch, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    @Override
    public Mono<Void> execute(String gatewayUrl) {
        return Mono.fromRunnable(() -> this.handshake.forEach(this::emit))
            .then(Mono.<Void>never());
    }

    @Override
    public Mono<CloseStatus> close(boolean allowResume) {
        return Mono.fromRunnable(this.dispatchSink::tryEmitComplete)
            .thenReturn(new CloseStatus(1000, "fake"));
    }

    @Override
    public Flux<Dispatch> dispatch() {
        return this.dispatchSink.asFlux();
    }

    @Override
    public Flux<GatewayPayload<?>> receiver() {
        return Flux.empty();
    }

    @Override
    public <T> Flux<T> receiver(Function<ByteBuf, Publisher<? extends T>> mapper) {
        return Flux.empty();
    }

    @Override
    public Sinks.Many<GatewayPayload<?>> sender() {
        return this.senderSink;
    }

    @Override
    public Mono<Void> sendBuffer(Publisher<ByteBuf> publisher) {
        return Flux.from(publisher).doOnNext(ByteBuf::release).then();
    }

    @Override
    public int getShardCount() {
        return this.shardCount;
    }

    @Override
    public String getSessionId() {
        return "fake-session";
    }

    @Override
    public int getSequence() {
        return 0;
    }

    @Override
    public Flux<GatewayConnection.State> stateEvents() {
        return Flux.empty();
    }

    @Override
    public Mono<Boolean> isConnected() {
        return Mono.just(true);
    }

    @Override
    public Duration getResponseTime() {
        return Duration.ZERO;
    }

}
