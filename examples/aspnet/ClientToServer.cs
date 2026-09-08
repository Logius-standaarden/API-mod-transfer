using System.Security.Cryptography;

/// <summary>
/// Push pattern (client-to-server)
/// https://logius-standaarden.github.io/API-mod-transfer/#push-pattern
/// </summary>
public static class ClientToServer
{
    private static readonly Dictionary<Guid, MetadataResource> Registrations = new();

    public static void MapClientToServer(this WebApplication app)
    {
        var group = app.MapGroup("/client-to-server").WithTags("client-to-server");

        // /transfer/upload: "Request an upload location..."
        group.MapPost("", (MetadataResourcePost upload) =>
        {
            var fileIdentifier = Guid.NewGuid();
            var metadata = new MetadataResource(
                upload.FileName,
                upload.ContentType,
                upload.Size,
                $"/client-to-server/{fileIdentifier}/content");

            Registrations[fileIdentifier] = metadata;

            return Results.Created($"/client-to-server/{fileIdentifier}", metadata);
        });

        group.MapGet("/{fileIdentifier:guid}", (Guid fileIdentifier) =>
            Registrations.TryGetValue(fileIdentifier, out var metadata)
                ? Results.Ok(metadata)
                : Results.Problem(title: "Could not obtain file", detail: "File does not exist",
                    statusCode: StatusCodes.Status404NotFound));

        // /transfer/upload: "...then use HTTP PUT to push"
        group.MapPut("/{fileIdentifier:guid}/content", async (Guid fileIdentifier, HttpRequest request) =>
        {
            if (!Registrations.TryGetValue(fileIdentifier, out var metadata))
                return Results.Problem(title: "Could not accept file", detail: "Not a registered upload location.",
                    statusCode: StatusCodes.Status404NotFound);

            using var buffer = new MemoryStream();
            await request.Body.CopyToAsync(buffer);
            var content = buffer.ToArray();

            // /transfer/metadata: size check
            if (content.Length != metadata.Size)
                return Results.Problem(title: "Could not accept file",
                    detail: $"Expected {metadata.Size} bytes, received {content.Length}",
                    statusCode: StatusCodes.Status400BadRequest);

            // /transfer/integrity: "MUST include Content-Digest"
            var claimedDigest = request.Headers["Content-Digest"].ToString();
            if (string.IsNullOrEmpty(claimedDigest))
                return Results.Problem(title: "Could not accept file",
                    detail: "Missing Content-Digest",
                    statusCode: StatusCodes.Status400BadRequest);

            // /transfer/integrity: "API MUST compute the digest of the received content and verify it matches Content-Digest"
            if (claimedDigest != $"sha-256=:{Convert.ToBase64String(SHA256.HashData(content))}:")
                return Results.Problem(title: "Could not accept file",
                    detail: "Content-Digest does not match the received content",
                    statusCode: StatusCodes.Status400BadRequest);

            return Results.NoContent();
        });
    }
}
