package org.acme;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
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
        var largeFiles = IOUtils.readLines(resourceAsStream, StandardCharsets.UTF_8);

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
    public Response getFileContent(
            @PathParam("fileIdentifier") String fileIdentifier,
            @HeaderParam("Range") Optional<String> rangeHeader,
            @HeaderParam("If-Range") Optional<String> ifRangeHeader) {
        var fullFileContent = this.getFileContent(fileIdentifier);
        var entityTag = computeEntityTag(fullFileContent);
        var digestForFullFile = computeContentDigest(fullFileContent);
        if (rangeHeader.isEmpty() || doesNotMatchIfRangeHeader(ifRangeHeader, entityTag)) {
            return Response.ok(fullFileContent)
                    .tag(new EntityTag(entityTag))
                    .header("Accept-Ranges", "bytes")
                    .header("Repr-Digest", digestForFullFile)
                    .header("Content-Digest", digestForFullFile)
                    .build();
        }

        var rangeHeaderIndices = getRangeHeaderIndices(fullFileContent, rangeHeader.get());
        int startIndex = rangeHeaderIndices.startIndex();
        int endIndex = rangeHeaderIndices.endIndex();

        var totalFileSize = fullFileContent.length();
        if (startIndex > totalFileSize || endIndex > totalFileSize) {
            return Response.status(Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Accept-Ranges", "bytes")
                    .header("Content-Range", "bytes */%s".formatted(totalFileSize))
                    .build();
        }
        var fileRangeContent = fullFileContent.substring(startIndex, endIndex);
        return Response.ok(fileRangeContent)
                .tag(new EntityTag(entityTag))
                .status(Response.Status.PARTIAL_CONTENT)
                .header("Accept-Ranges", "bytes")
                .header(
                        "Content-Range",
                        "bytes %s-%s/%s".formatted(startIndex, endIndex, totalFileSize))
                .header("Content-Digest", computeContentDigest(fileRangeContent))
                .header("Repr-Digest", digestForFullFile)
                .build();
    }

    /**
     * Returns true iff the ifRangeHeader is present and it does not match the computed hash of
     * fullFileContent. This means that if the header is not present, it will be treated as okay.
     *
     * @param ifRangeHeader The contents of the "If-Range" header, if any
     * @param entityTag The computed entityTag of the file content
     * @return true iff the ifRangeHeader is present and it does not match the computed hash of
     *     fullFileContent
     */
    private Boolean doesNotMatchIfRangeHeader(Optional<String> ifRangeHeader, String entityTag) {
        return ifRangeHeader
                .map((ifRange) -> !ifRange.equals("\"%s\"".formatted(entityTag)))
                .orElse(false);
    }

    private String computeContentDigest(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return "sha-256=:%s:".formatted(Base64.getEncoder().encodeToString(digest));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String computeEntityTag(String fullFileContent) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            byte[] digest = md.digest(fullFileContent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getFileContent(String fileIdentifier) {
        var classloader = Thread.currentThread().getContextClassLoader();
        try (var inputStream =
                classloader.getResourceAsStream("large-files/" + fileIdentifier + ".txt")) {
            if (inputStream == null) {
                throw HttpProblem.builder()
                        .withTitle("Could not obtain file")
                        .withStatus(Response.Status.BAD_REQUEST)
                        .withDetail("File does not exist")
                        .build();
            }
            return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining());
        } catch (IOException e) {
            throw HttpProblem.builder()
                    .withTitle("Could not obtain file")
                    .withStatus(Response.Status.BAD_REQUEST)
                    .withDetail("File could not be obtained")
                    .build();
        }
    }

    private static RangeHeaderIndices getRangeHeaderIndices(
            String fullFileContent, String rangeHeaderContent) {
        var ranges = getRangeHeaderStrings(rangeHeaderContent);
        if (ranges[0].isEmpty()) {
            var fileLength = fullFileContent.length();
            try {
                var startIndex = fileLength - Integer.parseInt(ranges[1]);
                if (startIndex < 0) {
                    throw HttpProblem.builder()
                            .withTitle("Invalid range header content")
                            .withStatus(Response.Status.BAD_REQUEST)
                            .withDetail("Negative range larger than file content")
                            .build();
                }

                return new RangeHeaderIndices(startIndex, fileLength);
            } catch (NumberFormatException e) {
                throw HttpProblem.builder()
                        .withTitle("Invalid range header content")
                        .withStatus(Response.Status.BAD_REQUEST)
                        .withDetail("Range header values should be numbers")
                        .build();
            }
        }
        int startIndex;
        int endIndex;
        try {
            startIndex = Integer.parseInt(ranges[0]);
            endIndex = Integer.parseInt(ranges[1]);
        } catch (NumberFormatException e) {
            throw HttpProblem.builder()
                    .withTitle("Invalid range header content")
                    .withStatus(Response.Status.BAD_REQUEST)
                    .withDetail("Range header values should be numbers")
                    .build();
        }
        if (endIndex <= startIndex) {
            throw HttpProblem.builder()
                    .withTitle("Invalid range header content")
                    .withStatus(Response.Status.BAD_REQUEST)
                    .withDetail("End index should be larger than start")
                    .build();
        }
        return new RangeHeaderIndices(startIndex, endIndex);
    }

    private static String[] getRangeHeaderStrings(String rangeHeaderContent) {
        if (!rangeHeaderContent.startsWith("bytes=")) {
            throw HttpProblem.builder()
                    .withTitle("Invalid range header content")
                    .withStatus(Response.Status.BAD_REQUEST)
                    .withDetail("Range header should start with \"bytes=\"")
                    .build();
        }
        var ranges = rangeHeaderContent.substring("bytes=".length()).split("-");
        if (ranges.length != 2) {
            throw HttpProblem.builder()
                    .withTitle("Invalid range header content")
                    .withStatus(Response.Status.BAD_REQUEST)
                    .withDetail("Range header should have two values for range")
                    .build();
        }
        return ranges;
    }

    private record RangeHeaderIndices(int startIndex, int endIndex) {}
}
