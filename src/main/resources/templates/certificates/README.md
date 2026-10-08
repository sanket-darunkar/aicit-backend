# Certificate PDF Templates

Place the two official AICIT certificate PDF template files here.

## Required Files

| Filename                       | Used for                              |
|-------------------------------|---------------------------------------|
| `aicit_certificate.pdf`        | All normal/listed courses — BLUE      |
| `aicit_typing_certificate.pdf` | Typing courses (name contains "typing") — ORANGE |
| `sign_exam_executive.png`      | Exam Executive signature (overlaid on BLUE cert) |
| `sign_head_institute.png`      | Head of Institute signature (overlaid on TYPING cert, bottom-left) |

> Signature PNGs are **optional**. If absent, certificates still generate —
> the signature is simply skipped. The typing template already has the
> Exam-Executive signature baked into the design; only the Head-of-Institute
> signature is overlaid there.

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

## Text Field Positions (origin = bottom-left)

> Coordinates below were measured directly from the template rasters and
> verified against the official reference certificates. Centred fields are
> positioned by text/box width in code, so only the baseline Y (and the
> horizontal band for in-line blanks) matters.

### AICIT Certificate — `aicit_certificate.pdf` (A4, 595.28 × 841.89)

| Field            | X band / X    | Baseline Y | Font | Notes |
|------------------|---------------|-----------|------|-------|
| Student Photo    | 448 (90×105)  | 472       | —    | top-right of blank band |
| Student Name     | centred       | 495       | 20   | bold navy |
| ATC / Institute  | centred       | 470       | 10   | bold navy |
| Grade value      | 388–452       | 366       | 13   | dotted blank after "with" |
| Marks / %        | 508–582       | 366       | 12   | dotted blank after "Grade" |
| Course Name      | centred       | 300       | 12   | own line above "Design and..." |
| Course Duration  | centred       | 290       | 7.5  | dark grey |
| Exam Exec. sign  | 392 (106×42)  | 132       | —    | above "EXAM EXECUTIVE" |
| Certificate No   | 48            | 68        | 8    | — |
| Issue Date       | 48            | 55        | 8    | — |
| QR Code          | 92 (74×74)    | 150       | —    | bottom-left, below EGAC band |

### Typing Certificate — `aicit_typing_certificate.pdf` (US Letter, 612 × 792)

| Field            | X band / X    | Baseline Y | Font | Notes |
|------------------|---------------|-----------|------|-------|
| Student ID       | 62–192        | 513       | 9    | box 1 |
| Center Code      | 192–328       | 513       | 9    | box 2 |
| ATC Name         | 328–550       | 513       | 7.5  | box 3 |
| Student Name     | 342–552       | 480       | 13   | WITHIN SIGNED box |
| Venue (held at)  | 150           | 348       | 9    | top sub-line |
| Month            | 160           | 330       | 9    | bottom sub-line |
| Grade value      | 405–525       | 330       | 11   | between "in" and Centre/Grade |
| Subject Name     | 55–192        | 288       | 7    | marks table |
| Max Marks        | 240–335       | 288       | 8    | marks table |
| Min Marks        | 335–440       | 288       | 8    | marks table |
| Marks Obtained   | 440–545       | 288       | 8    | marks table |
| Student Photo    | centred (80×95)| 148      | —    | centre photo box |
| Head sign.       | 145 (120×46)  | 158       | —    | above "Signature of Head" |
| Certificate No   | W−195         | 28        | 8    | bottom-right |
