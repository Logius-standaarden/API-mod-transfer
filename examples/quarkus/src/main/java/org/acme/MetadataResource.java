package org.acme;

/**
 * <a href="https://logius-standaarden.github.io/API-mod-transfer/#dfn-metadata-resource">Definition
 * in standard</a>
 */
public record MetadataResource(String fileName, String contentType, int size, String contentUri) {}
