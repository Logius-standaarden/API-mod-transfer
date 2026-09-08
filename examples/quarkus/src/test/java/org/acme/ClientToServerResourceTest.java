package org.acme;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.net.URI;

@QuarkusTest
class ClientToServerResourceTest {

    private static final String NON_REGISTERED_FILE_IDENTIFIER_CONTENT_LOCATION =
            "/client-to-server/files/f5f4b170-3c78-4dd9-9cd6-8218d40bf3e9/content";
    private static final String LARGE_FILE_NAME = "large-file.txt";
    private static final String REPR_DIGEST_FOR_FILE =
            "sha-256=:Levte/OrSs4iJPUpNF81GcVa5NQVMq8ZNIGSonN86zE=:";
    public static final String LARGE_FILE_CONTENT = "This is an uploaded file that is really large";

    @Test
    void testRegisterFileWithContent() {
        given().contentType(ContentType.JSON)
                .body(
                        new CreateFileRecord(
                                LARGE_FILE_NAME, "text/plain", LARGE_FILE_CONTENT.length()))
                .when()
                .post("/client-to-server")
                .then()
                .statusCode(201)
                .contentType("application/json")
                .header(
                        "Location",
                        (response) -> {
                            var fileIdentifier =
                                    URI.create(response.path("contentUri")).getPath().split("/")[3];
                            return Matchers.equalTo(
                                    RestAssured.baseURI
                                            + ":"
                                            + RestAssured.port
                                            + "/client-to-server/metadata/"
                                            + fileIdentifier);
                        })
                .body("size", Matchers.equalTo(LARGE_FILE_CONTENT.length()))
                .body("fileName", Matchers.equalTo(LARGE_FILE_NAME))
                .body("contentType", Matchers.equalTo("text/plain"))
                .body(
                        "contentUri",
                        Matchers.matchesPattern("/client-to-server/files/[^/]+/content"));
    }

    @Test
    void testUploadFileWithContent() {
        var createdMetadataResource =
                given().contentType(ContentType.JSON)
                        .body(
                                new CreateFileRecord(
                                        LARGE_FILE_NAME, "text/plain", LARGE_FILE_CONTENT.length()))
                        .when()
                        .post("/client-to-server")
                        .andReturn();

        given().contentType("text/plain")
                .header("Content-Digest", REPR_DIGEST_FOR_FILE)
                .body(LARGE_FILE_CONTENT)
                .when()
                .put(createdMetadataResource.path("contentUri").toString())
                .then()
                .statusCode(204)
                .header("Repr-Digest", REPR_DIGEST_FOR_FILE);
    }

    @Test
    void testUploadFileWithDifferentContentLength() {
        var createdMetadataResource =
                given().contentType(ContentType.JSON)
                        .body(
                                new CreateFileRecord(
                                        LARGE_FILE_NAME, "text/plain", LARGE_FILE_CONTENT.length()))
                        .when()
                        .post("/client-to-server")
                        .andReturn();

        var contentUri = createdMetadataResource.path("contentUri").toString();
        given().contentType("text/plain")
                .header("Content-Digest", REPR_DIGEST_FOR_FILE)
                .body(LARGE_FILE_CONTENT.substring(0, 5))
                .when()
                .put(contentUri)
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", Matchers.equalTo(400))
                .body("title", Matchers.equalTo("File size mismatch"))
                .body(
                        "detail",
                        Matchers.equalTo(
                                "Computed file length 5 is not equal to expected file length 45"))
                .body("instance", Matchers.equalTo(contentUri));
    }

    @Test
    void testUploadFileWithMissingDigestResultsInBadRequest() {
        given().contentType("text/plain")
                .body(LARGE_FILE_CONTENT)
                .when()
                .put(NON_REGISTERED_FILE_IDENTIFIER_CONTENT_LOCATION)
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", Matchers.equalTo(400))
                .body("title", Matchers.equalTo("Missing Content-Digest"))
                .body("detail", Matchers.equalTo("The header Content-Digest was not provided"))
                .body(
                        "instance",
                        Matchers.equalTo(NON_REGISTERED_FILE_IDENTIFIER_CONTENT_LOCATION));
    }

    @Test
    void testUploadFileWithInvalidDigestResultsInBadRequest() {
        var createdMetadataResource =
                given().contentType(ContentType.JSON)
                        .body(
                                new CreateFileRecord(
                                        LARGE_FILE_NAME, "text/plain", LARGE_FILE_CONTENT.length()))
                        .when()
                        .post("/client-to-server")
                        .andReturn();

        var contentUri = createdMetadataResource.path("contentUri").toString();
        given().contentType("text/plain")
                .header("Content-Digest", "sha-256=:wrong:")
                .body(LARGE_FILE_CONTENT)
                .when()
                .put(contentUri)
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", Matchers.equalTo(400))
                .body("title", Matchers.equalTo("Content-Digest mismatch"))
                .body(
                        "detail",
                        Matchers.equalTo(
                                "Computed digest based on file content does not match provided Content-Digest"))
                .body("instance", Matchers.equalTo(contentUri));
    }

    // Test dat file length wel overeenkomt met de geregistreerde metadata resource
}
