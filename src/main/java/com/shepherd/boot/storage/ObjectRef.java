package com.shepherd.boot.storage;

import org.jspecify.annotations.Nullable;

public record ObjectRef(String bucket, String key, @Nullable String eTag, long size) {}
