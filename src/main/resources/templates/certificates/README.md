# Certificate PDF Templates

Place the two official AICIT certificate PDF template files here.

## Required Files

| Filename                       | Used for                              |
|-------------------------------|---------------------------------------|
| `aicit_certificate.pdf`        | All normal/listed courses — BLUE      |
| `aicit_typing_certificate.pdf` | Typing courses (name contains "typing") — ORANGE |

## How Templates Are Used

The `PdfGeneratorService` detects whether these files exist at startup via
`ClassPathResource`. If a template file is present, it is used as the base layer
(loaded via PDFBox) and student/certificate data is overlaid using iText 8.

If a template file is **absent**, the service falls back to the fully-programmatic
iText 8 layout — so the system always works even without the template files.

## Placement Instructions

1. Export your official certificate designs from the original source (Photoshop / Illustrator / Word)
   as a single-page PDF with all logos, borders and signatures baked in — but with the
   student data areas left blank.

2. Copy the exported files here:
   - `src/main/resources/templates/certificates/aicit_certificate.pdf`
   - `src/main/resources/templates/certificates/aicit_typing_certificate.pdf`

3. Restart the Spring Boot application. No code changes are required.
   The service will automatically detect the files and switch to overlay mode.

## Text Field Positions (A4 Portrait, origin = bottom-left)

### AICIT Certificate (aicit_certificate.pdf)

| Field              | Approx X | Approx Y | Font Size |
|-------------------|---------|---------|-----------|
| Student Name       | 148     | 530     | 22        |
| ATC / Institute    | 148     | 495     | 10        |
| Grade              | 420     | 460     | 14        |
| Marks              | 290     | 460     | 14        |
| Course Name        | 148     | 430     | 11        |
| Certificate No     | 60      | 60      | 8         |
| Issue Date         | 60      | 48      | 8         |
| QR Code            | 380     | 30      | —         |

### Typing Certificate (aicit_typing_certificate.pdf)

| Field              | Approx X | Approx Y | Font Size |
|-------------------|---------|---------|-----------|
| Student ID         | 85      | 630     | 9         |
| Center Code        | 230     | 630     | 9         |
| ATC Name           | 375     | 630     | 9         |
| Student Name       | 148     | 560     | 18        |
| Subject Name       | 75      | 345     | 8.5       |
| Speed WPM          | 255     | 345     | 8.5       |
| Max Marks          | 330     | 345     | 8.5       |
| Min Marks          | 410     | 345     | 8.5       |
| Marks Obtained     | 490     | 345     | 8.5       |
| Month              | 200     | 385     | 9         |
| Grade              | 430     | 385     | 9         |
| Certificate No     | 390     | 30      | 8         |
