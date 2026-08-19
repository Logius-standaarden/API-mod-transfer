/// <summary>https://logius-standaarden.github.io/API-mod-transfer/#dfn-metadata-resource</summary>
public record MetadataResource(string FileName, string ContentType, long Size, string ContentUri);

/// <summary>Partial metadata resource sent in POST for PUSH method</summary>
public record MetadataResourcePost(string FileName, string ContentType, long Size);