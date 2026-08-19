package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class ServerToClientResourceTest {

    private static final String LARGE_FILE_CONTENT_LOCATION =
            "/server-to-client/80444340-6d5b-4e6c-8192-b1b935502790/content";
    private static final String REPR_DIGEST_FOR_FILE =
            "sha-256=:9R51cqDzX3bcID+v1/vfdTZ0EJ7uUjrHhZl9FEBI7JI=:";
    private static final String ETAG_FOR_FILE =
            "\"f51e7572a0f35f76dc203fafd7fbdf753674109eee523ac785997d144048ec92\"";
    public static final String LARGE_FILE_CONTENT = "This is a file that is really large";

    @Test
    void testMetadataResource() {
        given().when()
                .get("/server-to-client/80444340-6d5b-4e6c-8192-b1b935502790")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("size", Matchers.equalTo(35))
                .body("fileName", Matchers.equalTo("80444340-6d5b-4e6c-8192-b1b935502790.txt"))
                .body("contentType", Matchers.equalTo("text/plain"))
                .body("contentUri", Matchers.equalTo(LARGE_FILE_CONTENT_LOCATION));
    }

    @Test
    void testMissingMetadataResourceResultsInBadRequest() {
        given().when()
                .get("/server-to-client/does-not-exist")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", Matchers.equalTo(400))
                .body("title", Matchers.equalTo("Could not obtain file"))
                .body("detail", Matchers.equalTo("File does not exist"))
                .body("instance", Matchers.equalTo("/server-to-client/does-not-exist"));
    }

    @Test
    void testNoRangeReturnsFullFileContent() {
        given().when()
                .get(LARGE_FILE_CONTENT_LOCATION)
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .body(Matchers.equalTo(LARGE_FILE_CONTENT))
                .header("Accept-Ranges", "bytes")
                .header("Repr-Digest", REPR_DIGEST_FOR_FILE)
                .header("ETag", ETAG_FOR_FILE);
    }

    @Test
    void testRangeStartBeforeEndResultsInBadRequest() {
        given().header("Range", "bytes=13-10")
                .when()
                .get(LARGE_FILE_CONTENT_LOCATION)
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", Matchers.equalTo(400))
                .body("title", Matchers.equalTo("Invalid range header content"))
                .body("detail", Matchers.equalTo("End index should be larger than start"))
                .body("instance", Matchers.equalTo(LARGE_FILE_CONTENT_LOCATION));
    }

    @Test
    void testRangeStartNotANumberResultsInBadRequest() {
        given().header("Range", "bytes=foo-10")
                .when()
                .get(LARGE_FILE_CONTENT_LOCATION)
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", Matchers.equalTo(400))
                .body("title", Matchers.equalTo("Invalid range header content"))
                .body("detail", Matchers.equalTo("Range header values should be numbers"))
                .body("instance", Matchers.equalTo(LARGE_FILE_CONTENT_LOCATION));
    }

    @Test
    void testRangeEndNotANumberResultsInBadRequest() {
        given().header("Range", "bytes=10-foo")
                .when()
                .get(LARGE_FILE_CONTENT_LOCATION)
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", Matchers.equalTo(400))
                .body("title", Matchers.equalTo("Invalid range header content"))
                .body("detail", Matchers.equalTo("Range header values should be numbers"))
                .body("instance", Matchers.equalTo(LARGE_FILE_CONTENT_LOCATION));
    }

    @Test
    void testRangeWrongSeparationResultsInBadRequest() {
        given().header("Range", "bytes=10 foo")
                .when()
                .get(LARGE_FILE_CONTENT_LOCATION)
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", Matchers.equalTo(400))
                .body("title", Matchers.equalTo("Invalid range header content"))
                .body("detail", Matchers.equalTo("Range header should have two values for range"))
                .body("instance", Matchers.equalTo(LARGE_FILE_CONTENT_LOCATION));
    }

    @Test
    void testRangeDoesNotStartWithBytesResultsInBadRequest() {
        given().header("Range", "string=10-13")
                .when()
                .get(LARGE_FILE_CONTENT_LOCATION)
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", Matchers.equalTo(400))
                .body("title", Matchers.equalTo("Invalid range header content"))
                .body("detail", Matchers.equalTo("Range header should start with \"bytes=\""))
                .body("instance", Matchers.equalTo(LARGE_FILE_CONTENT_LOCATION));
    }

    @Test
    void testRangeStartTooLarge() {
        given().header("Range", "bytes=52-56")
                .when()
                .get(LARGE_FILE_CONTENT_LOCATION)
                .then()
                .statusCode(416)
                .header("Accept-Ranges", "bytes")
                .header("Content-Range", "bytes */35");
    }

    @Test
    void testSmallRangeReturnsPartialContent() {
        given().header("Range", "bytes=10-13")
                .when()
                .get(LARGE_FILE_CONTENT_LOCATION)
                .then()
                .statusCode(206)
                .contentType("text/plain")
                .body(Matchers.equalTo("fil"))
                .header("Accept-Ranges", "bytes")
                .header("Content-Range", "bytes 10-13/35")
                .header("Repr-Digest", REPR_DIGEST_FOR_FILE)
                .header("Content-Digest", "sha-256=:4BVa2bszMXotZewPuxyxoNDP/ZAnBgNgdTRYjWn2uhY=:")
                .header("ETag", ETAG_FOR_FILE);
    }

    @Test
    void testNegativeRangeLastPartOfFileReturnsPartialContent() {
        given().header("Range", "bytes=-15")
                .when()
                .get(LARGE_FILE_CONTENT_LOCATION)
                .then()
                .statusCode(206)
                .contentType("text/plain")
                .body(Matchers.equalTo("is really large"))
                .header("Accept-Ranges", "bytes")
                .header("Content-Range", "bytes 20-35/35")
                .header("Repr-Digest", REPR_DIGEST_FOR_FILE)
                .header("Content-Digest", "sha-256=:xSb2IKgXuZtC/PJUnGzSsJzchqwtETJQG/RjIrfjXNQ=:")
                .header("ETag", ETAG_FOR_FILE);
    }

    @Test
    void testNegativeRangeTooLargeResultsInBadRequest() {
        given().header("Range", "bytes=-130")
                .when()
                .get(LARGE_FILE_CONTENT_LOCATION)
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", Matchers.equalTo(400))
                .body("title", Matchers.equalTo("Invalid range header content"))
                .body("detail", Matchers.equalTo("Negative range larger than file content"))
                .body("instance", Matchers.equalTo(LARGE_FILE_CONTENT_LOCATION));
    }

    @Test
    void testMatchingIfRangeReturnsPartialContent() {
        given().header("Range", "bytes=10-13")
                .header("If-Range", ETAG_FOR_FILE)
                .when()
                .get(LARGE_FILE_CONTENT_LOCATION)
                .then()
                .statusCode(206)
                .contentType("text/plain")
                .body(Matchers.equalTo("fil"))
                .header("Accept-Ranges", "bytes")
                .header("Content-Range", "bytes 10-13/35")
                .header("Repr-Digest", REPR_DIGEST_FOR_FILE)
                .header("Content-Digest", "sha-256=:4BVa2bszMXotZewPuxyxoNDP/ZAnBgNgdTRYjWn2uhY=:")
                .header("ETag", ETAG_FOR_FILE);
    }

    @Test
    void testMismatchIfRangeReturnsFullContent() {
        given().header("Range", "bytes=10-13")
                .header("If-Range", "ThisDoesNotMatch")
                .when()
                .get(LARGE_FILE_CONTENT_LOCATION)
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .body(Matchers.equalTo(LARGE_FILE_CONTENT))
                .header("Accept-Ranges", "bytes")
                .header("Repr-Digest", REPR_DIGEST_FOR_FILE)
                .header("Content-Digest", REPR_DIGEST_FOR_FILE)
                .header("ETag", ETAG_FOR_FILE);
    }
}
