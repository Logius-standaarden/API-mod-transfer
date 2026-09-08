using System.Security.Cryptography;
using Microsoft.Net.Http.Headers;

var builder = WebApplication.CreateBuilder(args);
var app = builder.Build();

var largeFiles = Path.Combine(AppContext.BaseDirectory, "large-files");

FileInfo LargeFile(string fileIdentifier) =>
    new(Path.Combine(largeFiles, $"{Path.GetFileName(fileIdentifier)}.txt"));

app.MapGet("/server-to-client", () =>
{
    var identifiers = Directory.GetFiles(largeFiles, "*.txt").Select(Path.GetFileNameWithoutExtension);
    return $"The following files are available for download:\n\n- {string.Join("\n- ", identifiers)}\n";
});

app.MapGet("/server-to-client/{fileIdentifier}", (string fileIdentifier) =>
{
    var file = LargeFile(fileIdentifier);
    if (!file.Exists)
        return Results.Problem(title: "Could not obtain file", detail: "File does not exist",
            statusCode: StatusCodes.Status404NotFound);

    return Results.Ok(new MetadataResource(
        file.Name,
        "text/plain",
        file.Length,
        $"/server-to-client/{Path.GetFileNameWithoutExtension(file.Name)}/content"));
});

app.MapGet("/server-to-client/{fileIdentifier}/content", (string fileIdentifier, HttpResponse response) =>
{
    var file = LargeFile(fileIdentifier);
    if (!file.Exists)
        return Results.Problem(title: "Could not obtain file", detail: "File does not exist",
            statusCode: StatusCodes.Status404NotFound);

    var content = File.ReadAllBytes(file.FullName);
    var digest = SHA256.HashData(content);

    // /transfer/range
    var entityTag = new EntityTagHeaderValue($"\"{Convert.ToHexString(digest).ToLowerInvariant()}\"");

    // /transfer/integrity
    response.Headers["Repr-Digest"] = $"sha-256=:{Convert.ToBase64String(digest)}:";

    response.OnStarting(() =>
    {
        if (response.StatusCode is StatusCodes.Status200OK or StatusCodes.Status206PartialContent)
        {
            var served = content;
            if (ContentRangeHeaderValue.TryParse(response.Headers.ContentRange.ToString(), out var range)
                && range.From is not null && range.To is not null)
                served = content[(int)range.From.Value..((int)range.To.Value + 1)];

            response.Headers["Content-Digest"] = $"sha-256=:{Convert.ToBase64String(SHA256.HashData(served))}:";
        }

        return Task.CompletedTask;
    });

    return Results.File(content, "text/plain", enableRangeProcessing: true, entityTag: entityTag);
});

var registrations = new Dictionary<Guid, MetadataResource>();

// /transfer/upload: "Request an upload location..."
app.MapPost("/client-to-server", (MetadataResourcePost upload) =>
{
    var fileIdentifier = Guid.NewGuid();
    var metadata = new MetadataResource(
        upload.FileName,
        upload.ContentType,
        upload.Size,
        $"/client-to-server/{fileIdentifier}/content");

    registrations[fileIdentifier] = metadata;

    return Results.Created($"/client-to-server/{fileIdentifier}", metadata);
});

app.MapGet("/client-to-server/{fileIdentifier:guid}", (Guid fileIdentifier) =>
    registrations.TryGetValue(fileIdentifier, out var metadata)
        ? Results.Ok(metadata)
        : Results.Problem(title: "Could not obtain file", detail: "File does not exist",
            statusCode: StatusCodes.Status404NotFound));

// /transfer/upload: "...then use HTTP PUT to push"
app.MapPut("/client-to-server/{fileIdentifier:guid}/content", async (Guid fileIdentifier, HttpRequest request) =>
{
    if (!registrations.TryGetValue(fileIdentifier, out var metadata))
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

app.Run();
