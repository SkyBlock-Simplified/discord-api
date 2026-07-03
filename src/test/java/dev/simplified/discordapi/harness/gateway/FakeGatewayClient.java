package dev.simplified.discordapi.harness.gateway;

import discord4j.common.close.CloseStatus;
import discord4j.discordjson.json.gateway.Dispatch;
import discord4j.gateway.GatewayClient;
import discord4j.gateway.GatewayConnection;
import discord4j.gateway.json.GatewayPayload;
import io.netty.buffer.ByteBuf;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
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
 * <p>
 * Discord4J's real {@code DefaultGatewayClient} logs the gateway lifecycle; this stand-in replaces it, so
 * it logs its own handshake replay and close at DEBUG and every emitted dispatch at TRACE to fill the gap.
 */
@Log4j2
public final class FakeGatewayClient implements GatewayClient {

    private final Sinks.Many<Dispatch> dispatchSink = Sinks.many().replay().all();
    private final Sinks.Many<GatewayPayload<?>> senderSink = Sinks.many().multicast().onBackpressureBuffer();
    private final List<Dispatch> handshake;
    private final int shardCount;

    public FakeGatewayClient(@NotNull List<Dispatch> handshake, int shardCount) {
        this.handshake = List.copyOf(handshake);
        this.shardCount = shardCount;
    }

    /**
     * Pushes a dispatch (e.g. an {@code INTERACTION_CREATE}) into the live event pipeline.
     *
     * @param dispatch the dispatch to publish
     */
    public synchronized void emit(@NotNull Dispatch dispatch) {
        log.trace("Emitting {} into the dispatch pipeline", dispatch.getClass().getSimpleName());
        this.dispatchSink.emitNext(dispatch, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    @Override
    public @NotNull Mono<Void> execute(@NotNull String gatewayUrl) {
        return Mono.fromRunnable(() -> {
                log.debug("Fake gateway executing against {}; replaying {} handshake dispatch(es)", gatewayUrl, this.handshake.size());
                this.handshake.forEach(this::emit);
            })
            .then(Mono.never());
    }

    @Override
    public @NotNull Mono<CloseStatus> close(boolean allowResume) {
        log.debug("Closing fake gateway (allowResume={})", allowResume);
        return Mono.fromRunnable(this.dispatchSink::tryEmitComplete)
            .thenReturn(new CloseStatus(1000, "fake"));
    }

    @Override
    public @NotNull Flux<Dispatch> dispatch() {
        return this.dispatchSink.asFlux();
    }

    @Override
    public @NotNull Flux<GatewayPayload<?>> receiver() {
        return Flux.empty();
    }

    @Override
    public @NotNull <T> Flux<T> receiver(@NotNull Function<ByteBuf, Publisher<? extends T>> mapper) {
        return Flux.empty();
    }

    @Override
    public @NotNull Sinks.Many<GatewayPayload<?>> sender() {
        return this.senderSink;
    }

    @Override
    public @NotNull Mono<Void> sendBuffer(@NotNull Publisher<ByteBuf> publisher) {
        return Flux.from(publisher).doOnNext(ByteBuf::release).then();
    }

    @Override
    public int getShardCount() {
        return this.shardCount;
    }

    @Override
    public @NotNull String getSessionId() {
        return "fake-session";
    }

    @Override
    public int getSequence() {
        return 0;
    }

    @Override
    public @NotNull Flux<GatewayConnection.State> stateEvents() {
        return Flux.empty();
    }

    @Override
    public @NotNull Mono<Boolean> isConnected() {
        return Mono.just(true);
    }

    @Override
    public @NotNull Duration getResponseTime() {
        return Duration.ZERO;
    }

}
