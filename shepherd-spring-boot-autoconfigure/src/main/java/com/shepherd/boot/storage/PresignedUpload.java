package com.shepherd.boot.storage;

import java.net.URI;
import java.util.Map;

/**
 * @param requiredHeaders 前端 PUT 时**必须原样带上**的请求头。预签名若签入了
 *                        Content-Type，缺头会直接 403——这是前端直传最常踩的坑，
 *                        故显式返回而非让调用方猜。已剔除 host（由浏览器自动填）。
 */
public record PresignedUpload(URI url, Map<String, String> requiredHeaders) {}
