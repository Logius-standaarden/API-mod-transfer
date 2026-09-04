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

app.Run();
