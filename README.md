# Bulk File Uploader

This project is a small standalone Java application that reads a CSV file and uploads each referenced file to an HTTP endpoint using `multipart/form-data`.

It is intended for bulk migration jobs where each CSV row contains:

- a reference ID
- a document type
- a full file path on disk

The application sends one request per row and prints success, failure, and file-not-found messages to the console.

## Tech Stack

- Java 8
- Maven
- Apache HttpClient 5

## Project Structure

```text
src/main/java/com/example/BulkFileUploader.java
pom.xml
```

## What The App Does

For each row in the CSV file, the uploader:

1. reads `refId`
2. reads `type`
3. reads `file_path`
4. checks whether the file exists
5. sends a multipart POST request to the configured API
6. prints the server response and upload result

## Default Configuration

The current defaults in [src/main/java/com/example/BulkFileUploader.java](src/main/java/com/example/BulkFileUploader.java) are:

```java
private static final String DEFAULT_API_URL = "http://localhost:8090/apiAuthorization/vendor-attachment-upload";
private static final String DEFAULT_CSV_PATH = "C://VATTaxAttachments/Result_91.csv";
```

If needed, you can change these constants directly in the code, or pass values at runtime using command-line arguments.

## CSV Format

The CSV must include a header row and at least three columns:

```csv
ref_id,type,file_path
101,VAT,C:/VATTaxAttachments/Combined/vat/file1.pdf
102,TAX,C:/VATTaxAttachments/Combined/tax/file2.pdf
```

Expected meaning:

- `ref_id`: original database ID or reference ID
- `type`: document type such as `VAT` or `TAX`
- `file_path`: full absolute path to the file on your machine

The uploader includes basic CSV parsing that handles quoted values better than a simple `split(",")`.

## API Contract

The target endpoint must accept a multipart request with these form field names:

- `file`
- `type`
- `refId`

Example Spring Boot controller signature:

```java
@PostMapping("/apiAuthorization/vendor-attachment-upload")
public ResponseEntity<?> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam("type") String type,
        @RequestParam("refId") Long refId) {
    // handle upload
}
```

If your backend uses different request parameter names, update the uploader code accordingly.

## Prerequisites

Before running the uploader, make sure:

- Java 8 or later is installed
- Maven is installed
- the target API is running
- the CSV file path is correct
- the files listed in the CSV actually exist

## Build

Compile the project with:

```bash
mvn compile
```

## Run

Run with the default values from the Java class:

```bash
mvn exec:java
```

Run with custom values:

```bash
mvn exec:java -Dexec.args="C:/VATTaxAttachments/Result_91.csv http://localhost:8090/apiAuthorization/vendor-attachment-upload"
```

Argument order:

1. CSV path
2. API URL

## Example Console Output

```text
Server response: 200 - {"success":true,"message":"File uploaded successfully"}
[1/200] SUCCESS: C:\VATTaxAttachments\Combined\vat\file1.pdf
```

If a file does not exist:

```text
[2/200] FILE NOT FOUND: C:\VATTaxAttachments\Combined\vat\missing-file.pdf
```

If a CSV row is invalid:

```text
[3/200] INVALID CSV ROW: 123,VAT
```

## Error Handling

The application currently handles:

- missing CSV file
- empty CSV
- invalid CSV rows
- invalid `refId` values
- missing files on disk
- request/response exceptions during upload

At the end of execution, it prints a simple summary:

```text
Finished.
Success: 150
Failed : 50
```

## Notes

- The uploader sends files one by one, not in parallel.
- Response bodies are printed to the console for visibility during migration.
- The project includes `slf4j-simple` to avoid SLF4J binding warnings at runtime.

## Possible Improvements

Some useful next steps if you want to grow this tool:

- write failures to a separate log file
- export skipped or failed rows to a new CSV
- add retry support for temporary API failures
- support configurable timeouts
- support authentication headers if your API requires them
- add parallel upload support for faster processing
