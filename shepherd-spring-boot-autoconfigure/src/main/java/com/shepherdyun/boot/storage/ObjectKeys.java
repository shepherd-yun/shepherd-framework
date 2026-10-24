package com.shepherdyun.boot.storage;

public final class ObjectKeys {

    private ObjectKeys() {}

    /** 去掉前导斜杠、统一分隔符，并拒绝含 .. 的路径穿越。 */
    public static String normalize(String key) {
        if (key == null || key.isBlank()) {
            throw new StorageException(StorageErrorCode.INVALID_KEY, "对象 key 不能为空");
        }
        String normalized = key.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            throw new StorageException(StorageErrorCode.INVALID_KEY, "对象 key 不能只由斜杠组成：" + key);
        }
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new StorageException(StorageErrorCode.INVALID_KEY, "对象 key 不得包含 .. 路径段：" + key);
            }
        }
        return normalized;
    }
}
