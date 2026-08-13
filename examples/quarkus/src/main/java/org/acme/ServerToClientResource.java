package org.acme;

import jakarta.ws.rs.*;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;

@Path("/server-to-client")
@Tag(name = "server-to-client")
public class ServerToClientResource {

    public static final String LIST_OF_FILES_TEXT =
            """
          The following files are available for download:

          - %s
          """;

    @GET
    public String getAllFiles() {
        var resourceAsStream =
                ServerToClientResource.class.getClassLoader().getResourceAsStream("large-files/");
        if (resourceAsStream == null) {
            throw new RuntimeException("Could not retrieve files");
        }
        var largeFiles = IOUtils.readLines(resourceAsStream, Charsets.UTF_8);

        return LIST_OF_FILES_TEXT.formatted(String.join("\n- ", largeFiles));
    }

    @GET
    @Path("/{fileIdentifier}")
    public MetadataResource getMetadataResource(
            @PathParam("fileIdentifier") String fileIdentifier) {
        var fullFileContent = this.getFileContent(fileIdentifier);
        return new MetadataResource(
                fileIdentifier + ".txt",
                "text/plain",
                fullFileContent.length(),
                "/server-to-client/" + fileIdentifier + "/content");
    }

    @GET
    @Path("/{fileIdentifier}/content")
    public String getFileContent(
            @PathParam("fileIdentifier") String fileIdentifier,
            @HeaderParam("Range") Optional<String> rangeHeader) {
        var rangeHeaderIndices = getRangeHeaderIndices(rangeHeader);
        int startIndex = rangeHeaderIndices.startIndex();
        int endIndex = rangeHeaderIndices.endIndex();

        var fullFileContent = this.getFileContent(fileIdentifier);
        if (startIndex > fullFileContent.length() || endIndex > fullFileContent.length()) {
            throw new WebApplicationException(416);
        }
        return fullFileContent.substring(startIndex, endIndex);
    }

    private String getFileContent(String fileIdentifier) {
        var classloader = Thread.currentThread().getContextClassLoader();
        try (var inputStream =
                classloader.getResourceAsStream("large-files/" + fileIdentifier + ".txt")) {
            if (inputStream == null) {
                throw new BadRequestException("File could not be found");
            }
            return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining());
        } catch (IOException e) {
            throw new BadRequestException("File could not be found");
        }
    }

    private static RangeHeaderIndices getRangeHeaderIndices(Optional<String> rangeHeader) {
        var rangeHeaderContent =
                rangeHeader.orElseThrow(
                        () -> new BadRequestException("Did not specify range header"));
        if (!rangeHeaderContent.startsWith("bytes=")) {
            throw new BadRequestException("Range header should start with \"bytes=\"");
        }
        var ranges = rangeHeaderContent.substring("bytes=".length()).split("-");
        if (ranges.length != 2) {
            throw new BadRequestException("Range header should have two values for range");
        }
        int startIndex;
        int endIndex;
        try {
            startIndex = Integer.parseInt(ranges[0]);
            endIndex = Integer.parseInt(ranges[1]);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Range header values should be numbers");
        }
        if (endIndex <= startIndex) {
            throw new BadRequestException("End index should be larger than start");
        }
        return new RangeHeaderIndices(startIndex, endIndex);
    }

    private record RangeHeaderIndices(int startIndex, int endIndex) {}
}
