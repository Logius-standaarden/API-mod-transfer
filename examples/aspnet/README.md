# ASP.NET Core

Dit project gebruikt ASP.NET Core ([.NET 9.0](https://dotnet.microsoft.com/en-us/download/dotnet/9.0))
en Swashbuckle om de regels uit de Transfer module te demonstreren.

Bouw en draai het project als volgt.

```
dotnet build
dotnet run
```

De API is te bereiken op <http://localhost:5278> (OAS op <http://localhost:5278/openapi.json>).

## Pull: `/server-to-client`

Er staan twee voorbeeldbestanden klaar in `large-files/`.

```
curl -i localhost:5278/server-to-client
curl -i localhost:5278/server-to-client/80444340-6d5b-4e6c-8192-b1b935502790
curl -i -H "Range: bytes=10-13" localhost:5278/server-to-client/80444340-6d5b-4e6c-8192-b1b935502790/content
```

## Push: `/client-to-server`

Vraag uploadlocatie aan met `POST` en stuur vervolgens het bestand met `PUT`.

```
curl -i -X POST localhost:5278/client-to-server \
  -H "Content-Type: application/json" \
  -d '{"fileName":"upload.txt","contentType":"text/plain","size":45}'

curl -i -X PUT localhost:5278/client-to-server/<fileIdentifier>/content \
  -H "Content-Digest: sha-256=:Levte/OrSs4iJPUpNF81GcVa5NQVMq8ZNIGSonN86zE=:" \
  --data-binary "This is an uploaded file that is really large"
```
