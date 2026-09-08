using Microsoft.OpenApi.Models;
using Microsoft.OpenApi.Writers;
using Swashbuckle.AspNetCore.Swagger;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(options =>
{
    options.SwaggerDoc("v1", new OpenApiInfo
    {
        Version = "1.0.0",
        Title = "Voorbeeld Transfer Module in ASP.NET Core",
        Description = "Summier voorbeeld met pull- en push-patroon"
    });
    options.AddServer(new OpenApiServer { Url = "https://api.example.org/v1" });
});

builder.Services.AddProblemDetails(options =>
    options.CustomizeProblemDetails = context =>
    {
        context.ProblemDetails.Instance = context.HttpContext.Request.Path;
        context.ProblemDetails.Extensions.Remove("traceId");
    });

var app = builder.Build();

app.Use(async (context, next) =>
{
    context.Response.OnStarting(() =>
    {
        context.Response.Headers["API-Version"] = "1.0.0";
        return Task.CompletedTask;
    });

    await next();
});

app.MapServerToClient();
app.MapClientToServer();

app.MapGet("/openapi.json", async (ISwaggerProvider swaggerProvider, HttpResponse response) =>
{
    var document = swaggerProvider.GetSwagger("v1");

    using var textWriter = new StringWriter();
    document.SerializeAsV3(new OpenApiJsonWriter(textWriter));

    response.ContentType = "application/json";
    await response.WriteAsync(textWriter.ToString());
}).ExcludeFromDescription();

app.Run();
