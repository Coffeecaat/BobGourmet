package com.example.BobGourmet.DTO;

// Initial snapshots carry their room identity without changing existing topic payloads.
public record RoomSnapshotMessage<T>(String type, String roomId, T payload) {}
