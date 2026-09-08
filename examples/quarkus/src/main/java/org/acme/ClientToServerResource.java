package org.acme;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;

import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/client-to-server")
@Tag(name = "client-to-server")
public class ClientToServerResource {

    static final Map<UUID, MetadataResource> REGISTERED_METADATA_RESOURCES = new HashMap<>();

    @POST
    public Response registerNewFile(CreateFileRecord createFileRecord) {
        var fileIdentifier = UUID.randomUUID();
        var contentUri = "/client-to-server/files/" + fileIdentifier + "/content";
        var metadataResource =
                new MetadataResource(
                        createFileRecord.fileName(),
                        createFileRecord.contentType(),
                        createFileRecord.size(),
                        contentUri);
        REGISTERED_METADATA_RESOURCES.put(fileIdentifier, metadataResource);
        return Response.ok(metadataResource)
                .location(URI.create("/client-to-server/metadata/" + fileIdentifier))
                .status(Response.Status.CREATED)
                .build();
    }

    @GET
    @Path("/metadata/{fileIdentifier}")
    public MetadataResource getMetadataResource(
            @PathParam("fileIdentifier") String fileIdentifier) {
        MetadataResource metadataResource;
        try {
            metadataResource = REGISTERED_METADATA_RESOURCES.get(UUID.fromString(fileIdentifier));
        } catch (IllegalArgumentException e) {
            throw HttpProblem.builder()
                    .withTitle("Invalid file identifier")
                    .withStatus(Response.Status.BAD_REQUEST)
                    .withDetail("The file identifier %s is not a UUID".formatted(fileIdentifier))
                    .build();
        }
        if (metadataResource == null) {
            throw HttpProblem.builder()
                    .withTitle("Metadata resource does not exist")
                    .withStatus(Response.Status.NOT_FOUND)
                    .withDetail(
                            "The metadata resource for %s does not exist".formatted(fileIdentifier))
                    .build();
        }
        return metadataResource;
    }

    @PUT
    @Path("/files/{fileIdentifier}/content")
    public Response putFileContent(
            @PathParam("fileIdentifier") @NotNull String fileIdentifier,
            @HeaderParam("Content-Digest") String contentDigest,
            String fileContent) {
        if (contentDigest == null) {
            throw HttpProblem.builder()
                    .withTitle("Missing Content-Digest")
                    .withStatus(Response.Status.BAD_REQUEST)
                    .withDetail("The header Content-Digest was not provided")
                    .build();
        }

        var metadataResource = this.getMetadataResource(fileIdentifier);
        if (metadataResource.size() != fileContent.length()) {
            throw HttpProblem.builder()
                    .withTitle("File size mismatch")
                    .withStatus(Response.Status.BAD_REQUEST)
                    .withDetail(
                            "Computed file length %s is not equal to expected file length %s"
                                    .formatted(fileContent.length(), metadataResource.size()))
                    .build();
        }
        var computedContentDigest = ServerToClientResource.computeContentDigest(fileContent);
        if (!contentDigest.equals(computedContentDigest)) {
            throw HttpProblem.builder()
                    .withTitle("Content-Digest mismatch")
                    .withStatus(Response.Status.BAD_REQUEST)
                    .withDetail(
                            "Computed digest based on file content does not match provided Content-Digest")
                    .build();
        }

        return Response.ok()
                .status(Response.Status.NO_CONTENT)
                .header("Content-Type", "text/plain")
                .header("Repr-Digest", computedContentDigest)
                .build();
    }
}
