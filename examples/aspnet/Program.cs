var builder = WebApplication.CreateBuilder(args);
var app = builder.Build();

var largeFiles = Path.Combine(AppContext.BaseDirectory, "large-files");

app.MapGet("/server-to-client", () =>
{
    var identifiers = Directory.GetFiles(largeFiles, "*.txt").Select(Path.GetFileNameWithoutExtension);
    return $"The following files are available for download:\n\n- {string.Join("\n- ", identifiers)}\n";
});

app.MapGet("/server-to-client/{fileIdentifier}", (string fileIdentifier) =>
{
    var identifier = Path.GetFileName(fileIdentifier);

    var file = new FileInfo(Path.Combine(largeFiles, $"{identifier}.txt"));
    if (!file.Exists)
        return Results.Problem(title: "Could not obtain file", detail: "File does not exist",
            statusCode: StatusCodes.Status404NotFound);

    return Results.Ok(new MetadataResource(
        file.Name,
        "text/plain",
        file.Length,
        $"/server-to-client/{identifier}/content"));
});

app.Run();
