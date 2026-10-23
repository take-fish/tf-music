package com.fishmatrix.music.model;

import org.springframework.core.io.Resource;

/** 经过权限校验后的可流式音频资源。 */
public record TrackStream(Resource resource, String contentType, long size, String filename) {
}
