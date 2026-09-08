import assert from "node:assert";

const LARGE_FILE_IDENTIFIER = "80444340-6d5b-4e6c-8192-b1b935502790";
const LARGE_FILE_CONTENT = "This is a file that is really large";
const LARGE_FILE_CONTENT_LOCATION = "/server-to-client/" + LARGE_FILE_IDENTIFIER + "/content";
const LARGE_FILE_NAME = LARGE_FILE_IDENTIFIER + ".txt";
const SERVER_URL = process.env["SERVER_URL"] || "http://localhost:8080";
const CONTROLLER_BASE_PATH = SERVER_URL + "/server-to-client/";

const REPR_DIGEST_FOR_FILE = "sha-256=:9R51cqDzX3bcID+v1/vfdTZ0EJ7uUjrHhZl9FEBI7JI=:";
const ETAG_FOR_FILE = "\"f51e7572a0f35f76dc203fafd7fbdf753674109eee523ac785997d144048ec92\"";

describe("server-to-client", function () {
  it("can retrieve metadata resource", async function () {
    const response = await fetch(CONTROLLER_BASE_PATH + LARGE_FILE_IDENTIFIER);
    assert.equal(response.status, 200);
    const metadataResource = await response.json();
    assert.equal(metadataResource.contentType, "text/plain");
    assert.equal(metadataResource.fileName, LARGE_FILE_NAME);
    assert.equal(metadataResource.size, LARGE_FILE_CONTENT.length);
    assert.equal(metadataResource.contentUri, LARGE_FILE_CONTENT_LOCATION);
  });

  it("returns not found with non-existent file identifier", async function () {
    const metadataResourceResponse = await fetch(CONTROLLER_BASE_PATH + "does-no-exist");
    assert.equal(metadataResourceResponse.headers.get("Content-Type"), "application/problem+json");
    assert.equal(metadataResourceResponse.status, 404);
    const errorBody = await metadataResourceResponse.json();
    assert.equal(errorBody.status, 404);
    assert.equal(errorBody.title, "Could not obtain file");
    assert.equal(errorBody.detail, "File does not exist");
    assert.equal(errorBody.instance, "/server-to-client/does-no-exist");
  });

  it("returns full file content with no range header", async function () {
    const fileResponse = await fetch(SERVER_URL + LARGE_FILE_CONTENT_LOCATION);
    assert.equal(fileResponse.status, 200);
    const headers = fileResponse.headers;
    assert.equal(headers.get("Accept-Ranges"), "bytes");
    assert.equal(headers.get("Repr-Digest"), REPR_DIGEST_FOR_FILE);
    assert.equal(headers.get("ETag"), ETAG_FOR_FILE);
    assert.match(headers.get("Content-Type"), /^text\/plain/);
    const fileContent = await fileResponse.text();
    assert.equal(fileContent, LARGE_FILE_CONTENT);
  });

  describe("rejects retrieval with range", () => {
    it("start before end", async function () {
      const fileResponse = await fetch(SERVER_URL + LARGE_FILE_CONTENT_LOCATION, {
        headers: {
          "Range": "bytes=13-10",
        },
      });
      assert.equal(fileResponse.headers.get("Content-Type"), "application/problem+json");
      assert.equal(fileResponse.status, 400);
      const errorBody = await fileResponse.json();
      assert.equal(errorBody.status, 400);
      assert.equal(errorBody.title, "Invalid range header content");
      assert.equal(errorBody.detail, "End index should be larger than start");
      assert.equal(errorBody.instance, LARGE_FILE_CONTENT_LOCATION);
    });

    it("start not a number", async function () {
      const fileResponse = await fetch(SERVER_URL + LARGE_FILE_CONTENT_LOCATION, {
        headers: {
          "Range": "bytes=foo-10",
        },
      });
      assert.equal(fileResponse.headers.get("Content-Type"), "application/problem+json");
      assert.equal(fileResponse.status, 400);
      const errorBody = await fileResponse.json();
      assert.equal(errorBody.status, 400);
      assert.equal(errorBody.title, "Invalid range header content");
      assert.equal(errorBody.detail, "Range header values should be numbers");
      assert.equal(errorBody.instance, LARGE_FILE_CONTENT_LOCATION);
    });

    it("end not a number", async function () {
      const fileResponse = await fetch(SERVER_URL + LARGE_FILE_CONTENT_LOCATION, {
        headers: {
          "Range": "bytes=10-foo",
        },
      });
      assert.equal(fileResponse.headers.get("Content-Type"), "application/problem+json");
      assert.equal(fileResponse.status, 400);
      const errorBody = await fileResponse.json();
      assert.equal(errorBody.status, 400);
      assert.equal(errorBody.title, "Invalid range header content");
      assert.equal(errorBody.detail, "Range header values should be numbers");
      assert.equal(errorBody.instance, LARGE_FILE_CONTENT_LOCATION);
    });

    it("with wrong separator", async function () {
      const fileResponse = await fetch(SERVER_URL + LARGE_FILE_CONTENT_LOCATION, {
        headers: {
          "Range": "bytes=10 13",
        },
      });
      assert.equal(fileResponse.headers.get("Content-Type"), "application/problem+json");
      assert.equal(fileResponse.status, 400);
      const errorBody = await fileResponse.json();
      assert.equal(errorBody.status, 400);
      assert.equal(errorBody.title, "Invalid range header content");
      assert.equal(errorBody.detail, "Range header should have two values for range");
      assert.equal(errorBody.instance, LARGE_FILE_CONTENT_LOCATION);
    });

    it("with no bytes", async function () {
      const fileResponse = await fetch(SERVER_URL + LARGE_FILE_CONTENT_LOCATION, {
        headers: {
          "Range": "string=10-13",
        },
      });
      assert.equal(fileResponse.headers.get("Content-Type"), "application/problem+json");
      assert.equal(fileResponse.status, 400);
      const errorBody = await fileResponse.json();
      assert.equal(errorBody.status, 400);
      assert.equal(errorBody.title, "Invalid range header content");
      assert.equal(errorBody.detail, "Range header should start with \"bytes=\"");
      assert.equal(errorBody.instance, LARGE_FILE_CONTENT_LOCATION);
    });
  });

  describe("rejects range requests out of bounds", () => {
    it("rejects too large range request", async function () {
      const fileResponse = await fetch(SERVER_URL + LARGE_FILE_CONTENT_LOCATION, {
        headers: {
          "Range": "bytes=52-56",
        },
      });
      const headers = fileResponse.headers;
      assert.equal(headers.get("Accept-Ranges"), "bytes");
      assert.equal(headers.get("Content-Range"), "bytes */35");
      assert.equal(fileResponse.status, 416);
      const errorBody = await fileResponse.json();
      assert.equal(errorBody.status, 416);
      assert.equal(errorBody.title, "Invalid range header content");
      assert.equal(errorBody.detail, "Request range is too large");
      assert.equal(errorBody.instance, LARGE_FILE_CONTENT_LOCATION);
    });

    it("too large negative range", async function () {
      const fileResponse = await fetch(SERVER_URL + LARGE_FILE_CONTENT_LOCATION, {
        headers: {
          "Range": "bytes=-130",
        },
      });
      const headers = fileResponse.headers;
      assert.equal(headers.get("Accept-Ranges"), "bytes");
      assert.equal(headers.get("Content-Range"), "bytes */35");
      assert.equal(fileResponse.status, 416);
      const errorBody = await fileResponse.json();
      assert.equal(errorBody.status, 416);
      assert.equal(errorBody.title, "Invalid range header content");
      assert.equal(errorBody.detail, "Negative range larger than file content");
      assert.equal(errorBody.instance, LARGE_FILE_CONTENT_LOCATION);
    });
  });

  describe("for partial content", () => {
    it("returns small part", async function () {
      const fileResponse = await fetch(SERVER_URL + LARGE_FILE_CONTENT_LOCATION, {
        headers: {
          "Range": "bytes=10-13",
        },
      });
      const headers = fileResponse.headers;
      assert.equal(headers.get("Accept-Ranges"), "bytes");
      assert.equal(headers.get("Content-Digest"), "sha-256=:4BVa2bszMXotZewPuxyxoNDP/ZAnBgNgdTRYjWn2uhY=:");
      assert.equal(headers.get("Content-Range"), "bytes 10-12/35");
      assert.equal(headers.get("ETag"), ETAG_FOR_FILE);
      assert.equal(headers.get("Repr-Digest"), REPR_DIGEST_FOR_FILE);
      assert.equal(fileResponse.status, 206);
      assert.equal(await fileResponse.text(), "fil");
    });

    it("returns last part with negative range", async function () {
      const fileResponse = await fetch(SERVER_URL + LARGE_FILE_CONTENT_LOCATION, {
        headers: {
          "Range": "bytes=-15",
        },
      });
      const headers = fileResponse.headers;
      assert.equal(headers.get("Accept-Ranges"), "bytes");
      assert.equal(headers.get("Content-Digest"), "sha-256=:xSb2IKgXuZtC/PJUnGzSsJzchqwtETJQG/RjIrfjXNQ=:");
      assert.equal(headers.get("Content-Range"), "bytes 20-34/35");
      assert.equal(headers.get("ETag"), ETAG_FOR_FILE);
      assert.equal(headers.get("Repr-Digest"), REPR_DIGEST_FOR_FILE);
      assert.equal(fileResponse.status, 206);
      assert.equal(await fileResponse.text(), "is really large");
    });

    describe("with If-Range", () => {
      it("returns if matching", async function () {
        const fileResponse = await fetch(SERVER_URL + LARGE_FILE_CONTENT_LOCATION, {
          headers: {
            "Range": "bytes=10-13",
            "If-Range": ETAG_FOR_FILE,
          },
        });
        const headers = fileResponse.headers;
        assert.equal(headers.get("Accept-Ranges"), "bytes");
        assert.equal(headers.get("Content-Digest"), "sha-256=:4BVa2bszMXotZewPuxyxoNDP/ZAnBgNgdTRYjWn2uhY=:");
        assert.equal(headers.get("Content-Range"), "bytes 10-12/35");
        assert.equal(headers.get("ETag"), ETAG_FOR_FILE);
        assert.equal(headers.get("Repr-Digest"), REPR_DIGEST_FOR_FILE);
        assert.equal(fileResponse.status, 206);
        assert.equal(await fileResponse.text(), "fil");
      });

      it("returns full file when non matching", async function () {
        const fileResponse = await fetch(SERVER_URL + LARGE_FILE_CONTENT_LOCATION, {
          headers: {
            "Range": "bytes=10-13",
            "If-Range": "ThisDoesNotMatch",
          },
        });
        const headers = fileResponse.headers;
        assert.equal(headers.get("Accept-Ranges"), "bytes");
        assert.equal(headers.get("Content-Digest"), REPR_DIGEST_FOR_FILE);
        assert.equal(headers.get("ETag"), ETAG_FOR_FILE);
        assert.equal(headers.get("Repr-Digest"), REPR_DIGEST_FOR_FILE);
        assert.equal(fileResponse.status, 200);
        assert.equal(await fileResponse.text(), LARGE_FILE_CONTENT);
      });
    });
  });
});
