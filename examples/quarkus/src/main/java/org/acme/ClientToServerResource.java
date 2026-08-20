package org.acme;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.*;

import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.Optional;

@Path("/client-to-server")
@Tag(name = "client-to-server")
public class ClientToServerResource {

    static final String SUPPORTED_FILE_IDENTIFIER = "f5f4b170-3c78-4dd9-9cd6-8218d40bf3e9";

    @POST
    public Response registerNewFile(CreateFileRecord createFileRecord) {
        var contentUri = "/client-to-server/" + SUPPORTED_FILE_IDENTIFIER + "/content";
        var metadataResource =
                new MetadataResource(
                        createFileRecord.fileName(),
                        createFileRecord.contentType(),
                        createFileRecord.size(),
                        contentUri);
        return Response.ok(metadataResource)
                .location(URI.create(contentUri))
                .status(Response.Status.CREATED)
                .build();
    }

    @PUT
    @Path("/{fileIdentifier}/content")
    public Response putFileContent(
            @PathParam("fileIdentifier") String fileIdentifier,
            @HeaderParam("Content-Digest") String contentDigest,
            String fileContent) {
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
