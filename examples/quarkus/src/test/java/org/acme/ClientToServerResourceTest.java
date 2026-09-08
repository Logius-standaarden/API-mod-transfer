package org.acme;

import static io.restassured.RestAssured.given;
import static org.acme.ClientToServerResource.SUPPORTED_FILE_IDENTIFIER;

import io.quarkus.test.junit.QuarkusTest;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.net.URI;

@QuarkusTest
class ClientToServerResourceTest {

    private static final String LARGE_FILE_CONTENT_LOCATION =
            "/client-to-server/%s/content".formatted(SUPPORTED_FILE_IDENTIFIER);
    private static final String LARGE_FILE_NAME = "%s.txt".formatted(SUPPORTED_FILE_IDENTIFIER);
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
                        Matchers.equalTo(
                                RestAssured.baseURI
                                        + ":"
                                        + RestAssured.port
                                        + LARGE_FILE_CONTENT_LOCATION))
                .body("size", Matchers.equalTo(LARGE_FILE_CONTENT.length()))
                .body("fileName", Matchers.equalTo(LARGE_FILE_NAME))
                .body("contentType", Matchers.equalTo("text/plain"))
                .body("contentUri", Matchers.equalTo(LARGE_FILE_CONTENT_LOCATION));
    }

    @Test
    void testUploadFileWithContent() {
        given().contentType("text/plain")
                .body(LARGE_FILE_CONTENT)
                .when()
                .put(LARGE_FILE_CONTENT_LOCATION)
                .then()
                .statusCode(204)
                .contentType("text/plain")
                .header("Content-Digest", "sha-256=:Levte/OrSs4iJPUpNF81GcVa5NQVMq8ZNIGSonN86zE=:");
    }
}
