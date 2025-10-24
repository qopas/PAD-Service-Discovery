package com.pad.discovery.model;

public enum HeartbeatMode {
    DISABLED,   // No heartbeat expected, polling only
    OPTIONAL,   // Heartbeat can be sent but not enforced
    REQUIRED    // Heartbeat must be sent or service will be removed
}