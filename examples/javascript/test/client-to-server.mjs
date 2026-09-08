import assert from "node:assert";

const LARGE_FILE_CONTENT = "This is an uploaded file that is really large";
const LARGE_FILE_DIGEST = "sha-256=:Levte/OrSs4iJPUpNF81GcVa5NQVMq8ZNIGSonN86zE=:";
const LARGE_FILE_NAME = "large-file.txt";
const SERVER_URL = process.env["SERVER_URL"] || "http://localhost:8080";
const CONTROLLER_BASE_PATH = SERVER_URL + "/client-to-server/";

describe("client-to-server", function () {
  it("can create metadata resource", async function () {
    const response = await fetch(CONTROLLER_BASE_PATH, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        contentType: "text/plain",
        fileName: LARGE_FILE_NAME,
        size: LARGE_FILE_CONTENT.length,
      }),
    });
    assert.equal(response.status, 201);
    const metadataResource = await response.json();
    assert.equal(metadataResource.contentType, "text/plain");
    assert.equal(metadataResource.fileName, LARGE_FILE_NAME);
    assert.equal(metadataResource.size, LARGE_FILE_CONTENT.length);
    const fileIdentifier = metadataResource.contentUri.split('/')[5];
    assert.equal(response.headers.get("Location"), CONTROLLER_BASE_PATH + "metadata/" + fileIdentifier);
  });

  it("can upload a file with matching content", async function () {
    const metadataResourceResponse = await fetch(CONTROLLER_BASE_PATH, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        contentType: "text/plain",
        fileName: LARGE_FILE_NAME,
        size: LARGE_FILE_CONTENT.length,
      }),
    });
    const metadataResource = await metadataResourceResponse.json();
    const uploadedFileContentResponse = await fetch(metadataResource.contentUri, {
      method: "PUT",
      headers: {
        "Content-Digest": LARGE_FILE_DIGEST,
        "Content-Type": "text/plain"
      },
      body: LARGE_FILE_CONTENT,
    });
    console.log(await uploadedFileContentResponse.text())
    assert.equal(uploadedFileContentResponse.status, 204);
  });

  it("rejects upload with different content length", async function () {
    const metadataResourceResponse = await fetch(CONTROLLER_BASE_PATH, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        contentType: "text/plain",
        fileName: LARGE_FILE_NAME,
        size: LARGE_FILE_CONTENT.length,
      }),
    });
    const metadataResource = await metadataResourceResponse.json();
    const uploadedFileContentResponse = await fetch(metadataResource.contentUri, {
      method: "PUT",
      headers: {
        "Content-Digest": LARGE_FILE_DIGEST,
        "Content-Type": "text/plain"
      },
      body: LARGE_FILE_CONTENT.substring(0, 5),
    });
    assert.equal(uploadedFileContentResponse.headers.get("Content-Type"), "application/problem+json");
    assert.equal(uploadedFileContentResponse.status, 400);
    const errorBody = await uploadedFileContentResponse.json();
    assert.equal(errorBody.status, 400);
    assert.equal(errorBody.title, "File size mismatch");
    assert.equal(errorBody.detail, "Computed file length 5 is not equal to expected file length 45");
    assert.equal(errorBody.instance, metadataResource.contentUri.substring(SERVER_URL.length));
  });

  it("rejects upload with missing Content-Digest", async function () {
    const metadataResourceResponse = await fetch(CONTROLLER_BASE_PATH, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        contentType: "text/plain",
        fileName: LARGE_FILE_NAME,
        size: LARGE_FILE_CONTENT.length,
      }),
    });
    const metadataResource = await metadataResourceResponse.json();
    const uploadedFileContentResponse = await fetch(metadataResource.contentUri, {
      method: "PUT",
      headers: {
        "Content-Type": "text/plain"
      },
      body: LARGE_FILE_CONTENT,
    });
    assert.equal(uploadedFileContentResponse.headers.get("Content-Type"), "application/problem+json");
    assert.equal(uploadedFileContentResponse.status, 400);
    const errorBody = await uploadedFileContentResponse.json();
    assert.equal(errorBody.status, 400);
    assert.equal(errorBody.title, "Missing Content-Digest");
    assert.equal(errorBody.detail, "The header Content-Digest was not provided");
    assert.equal(errorBody.instance, metadataResource.contentUri.substring(SERVER_URL.length));
  });

  it("rejects upload with different Content-Digest", async function () {
    const metadataResourceResponse = await fetch(CONTROLLER_BASE_PATH, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        contentType: "text/plain",
        fileName: LARGE_FILE_NAME,
        size: LARGE_FILE_CONTENT.length,
      }),
    });
    const metadataResource = await metadataResourceResponse.json();
    const uploadedFileContentResponse = await fetch(metadataResource.contentUri, {
      method: "PUT",
      headers: {
        "Content-Digest": "sha-256=:wrong:",
        "Content-Type": "text/plain"
      },
      body: LARGE_FILE_CONTENT,
    });
    assert.equal(uploadedFileContentResponse.headers.get("Content-Type"), "application/problem+json");
    assert.equal(uploadedFileContentResponse.status, 400);
    const errorBody = await uploadedFileContentResponse.json();
    assert.equal(errorBody.status, 400);
    assert.equal(errorBody.title, "Content-Digest mismatch");
    assert.equal(errorBody.detail, "Computed digest based on file content does not match provided Content-Digest");
    assert.equal(errorBody.instance, metadataResource.contentUri.substring(SERVER_URL.length));
  });
});
