package com.digitalpetri.opcua.stack.client.fsm.states;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.digitalpetri.opcua.stack.client.fsm.ConnectionEvent;
import com.digitalpetri.opcua.stack.client.fsm.ConnectionState;
import com.digitalpetri.opcua.stack.client.fsm.ConnectionStateFsm;
import com.digitalpetri.opcua.stack.core.Stack;
import com.digitalpetri.opcua.stack.core.channel.ClientSecureChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReconnectDelay implements ConnectionState {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CompletableFuture<ClientSecureChannel> channelFuture = new CompletableFuture<>();

    private volatile ScheduledFuture<?> scheduledFuture;

    private final long delaySeconds;
    private volatile long secureChannelId;

    public ReconnectDelay(long delaySeconds, long secureChannelId) {
        this.delaySeconds = delaySeconds;
        this.secureChannelId = secureChannelId;
    }

    @Override
    public CompletableFuture<Void> activate(ConnectionEvent event, ConnectionStateFsm fsm) {
        if (scheduledFuture == null || (scheduledFuture != null && scheduledFuture.cancel(false))) {
            logger.debug("Scheduling reconnect in {} seconds...", delaySeconds);

            scheduledFuture = Stack.sharedScheduledExecutor().schedule(() -> {
                logger.debug("{} seconds elapsed; requesting reconnect.", delaySeconds);

                fsm.handleEvent(ConnectionEvent.ReconnectRequested);
            }, delaySeconds, TimeUnit.SECONDS);
        }

        return CF_VOID_COMPLETED;
    }

    @Override
    public CompletableFuture<Void> deactivate(ConnectionEvent event, ConnectionStateFsm fsm) {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }

        return CF_VOID_COMPLETED;
    }

    @Override
    public ConnectionState transition(ConnectionEvent event, ConnectionStateFsm fsm) {
        switch (event) {
            case DisconnectRequested:
                return new Disconnecting(null);

            case ReconnectRequested:
                return new ReconnectExecute(channelFuture, delaySeconds, secureChannelId);
        }

        return this;
    }

    @Override
    public CompletableFuture<ClientSecureChannel> getSecureChannel() {
        return channelFuture;
    }

    @Override
    public String toString() {
        return "ReconnectDelay{" +
                "delaySeconds=" + delaySeconds +
                ", secureChannelId=" + secureChannelId +
                '}';
    }

}